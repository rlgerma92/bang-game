//
// $Id$

package com.threerings.bang.server.persist;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;

import com.samskivert.io.PersistenceException;

import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.DatabaseLiaison;
import com.samskivert.jdbc.JDBCUtil;
import com.samskivert.jdbc.JORARepository;
import com.samskivert.jdbc.jora.FieldMask;
import com.samskivert.jdbc.jora.Table;

import com.threerings.bang.avatar.data.Look;

import com.threerings.bang.data.Handle;

import static com.threerings.bang.Log.log;

/**
 * Manages persistent information stored on a per-player basis.
 */
public class PlayerRepository extends JORARepository
{
    /** The database identifier used when establishing a database
     * connection. This value being <code>playerdb</code>. */
    public static final String PLAYER_DB_IDENT = "playerdb";

    /**
     * Constructs a new player repository with the specified connection
     * provider.
     *
     * @param conprov the connection provider via which we will obtain our
     * database connection.
     */
    public PlayerRepository (ConnectionProvider conprov)
        throws PersistenceException
    {
        super(conprov, PLAYER_DB_IDENT);
        _byNameMask = _ptable.getFieldMask();
        _byNameMask.setModified("accountName");
    }

    /**
     * Loads up the player record associated with the specified account.
     * Returns null if no matching record could be found.
     */
    public Player loadPlayer (String accountName)
        throws PersistenceException
    {
        return (Player)loadByExample(
            _ptable, new Player(accountName), _byNameMask);
    }

    /**
     * Insert a new player record into the repository and assigns them a
     * unique player id in the process. The {@link Player#created} field
     * will be filled in by this method if it is not already.
     */
    public void insertPlayer (final Player player)
        throws PersistenceException
    {
        if (player.created == null) {
            player.created = new Date(System.currentTimeMillis());
            player.lastSession = player.created;
        }
        player.playerId = insert(_ptable, player);
    }

    /**
     * Configures a player's handle, and gender.
     *
     * @return true if the player was properly configured, false if the
     * requested handle is a duplicate of an existing handle.
     */
    public boolean configurePlayer (int playerId, Handle handle, boolean isMale)
        throws PersistenceException
    {
        String gensql = isMale ?
            "| " + Player.IS_MALE_FLAG : "& " + ~Player.IS_MALE_FLAG;
        final String query = "update PLAYERS set FLAGS = FLAGS " + gensql +
            ", HANDLE = " + JDBCUtil.escape(handle.toString()) +
            " where PLAYER_ID = " + playerId;
        return executeUpdate(new Operation<Boolean>() {
            public Boolean invoke (Connection conn, DatabaseLiaison liaison)
                throws SQLException, PersistenceException
            {
                Statement stmt = conn.createStatement();
                try {
                    int mods = stmt.executeUpdate(query);
                    if (mods != 1) {
                        log.warning("Failed to config player [query=" + query +
                                    ", mods=" + mods + "].");
                        return Boolean.FALSE;
                    }

                } catch (SQLException sqe) {
                    if (liaison.isDuplicateRowException(sqe)) {
                        return Boolean.FALSE;
                    } else {
                        throw sqe;
                    }

                } finally {
                    JDBCUtil.close(stmt);
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * Deletes the specified player from the repository.
     */
    public void deletePlayer (final Player player)
        throws PersistenceException
    {
        delete(_ptable, player);
    }

    /**
     * Deducts the specified amount of scrip from the specified player's
     * account.
     */
    public void spendScrip (int playerId, int amount)
        throws PersistenceException
    {
        updateScrip("PLAYER_ID = " + playerId, amount, "spend");
    }

    /**
     * Adds the specified amount of scrip to the specified player's
     * account.
     */
    public void grantScrip (int playerId, int amount)
        throws PersistenceException
    {
        updateScrip("PLAYER_ID = " + playerId, amount, "grant");
    }

    /**
     * <em>Do not use this method!</em> It exists only because we must work
     * with the coin system which tracks players by name rather than id.
     */
    public void grantScrip (String accountName, int amount)
        throws PersistenceException
    {
        updateScrip("ACCOUNT_NAME = " + JDBCUtil.escape(accountName),
                    amount, "grant");
    }

    /**
     * Mimics the disabling of deleted players by renaming them to an
     * invalid value that we do in our user management system. This is
     * triggered by us receiving a player action indicating that the
     * player was deleted.
     */
    public void disablePlayer (String accountName, String disabledName)
        throws PersistenceException
    {
        if (update("update PLAYERS set ACCOUNT_NAME = " +
                   JDBCUtil.escape(disabledName) + " where ACCOUNT_NAME = " +
                   JDBCUtil.escape(accountName)) == 1) {
            log.info("Disabled deleted player [oname=" + accountName +
                     ", dname=" + disabledName + "].");
        }
    }

    /**
     * Note that a user's session has ended: increment their sessions, add in
     * the number of minutes spent online, set their last session time to now
     * and update any changed poses.
     */
    public void noteSessionEnded (int playerId, String[] poses,
                                  boolean[] changed, int minutes)
        throws PersistenceException
    {
        StringBuffer update = new StringBuffer();
        update.append("update PLAYERS set SESSIONS = SESSIONS + 1, ");
        update.append("SESSION_MINUTES = SESSION_MINUTES + ");
        update.append(minutes).append(", ");
        for (Look.Pose pose : EnumSet.allOf(Look.Pose.class)) {
            if (changed[pose.ordinal()]) {
                update.append(pose.getColumnName()).append(" = ");
                update.append(JDBCUtil.escape(poses[pose.ordinal()]));
                update.append(", ");
            }
        }
        update.append("LAST_SESSION = NOW() where PLAYER_ID=").append(playerId);
        checkedUpdate(update.toString(), 1);
    }

    @Override // documentation inherited
    protected void migrateSchema (Connection conn, DatabaseLiaison liaison)
        throws SQLException, PersistenceException
    {
        JDBCUtil.createTableIfMissing(conn, "PLAYERS", new String[] {
            "PLAYER_ID INTEGER NOT NULL AUTO_INCREMENT",
            "ACCOUNT_NAME VARCHAR(64) NOT NULL",
            "HANDLE VARCHAR(64) UNIQUE",
            "SCRIP INTEGER NOT NULL",
            "LOOK VARCHAR(" + Look.MAX_NAME_LENGTH + ") NOT NULL",
            "VICTORY_LOOK VARCHAR(" + Look.MAX_NAME_LENGTH + ") NOT NULL",
            "WANTED_LOOK VARCHAR(" + Look.MAX_NAME_LENGTH + ") NOT NULL",
            "CREATED DATETIME NOT NULL",
            "SESSIONS INTEGER NOT NULL",
            "SESSION_MINUTES INTEGER NOT NULL",
            "LAST_SESSION DATETIME NOT NULL",
            "FLAGS INTEGER NOT NULL",
            "PRIMARY KEY (PLAYER_ID)",
            "UNIQUE (ACCOUNT_NAME)",
        }, "");

        JDBCUtil.addColumn(
            conn, "PLAYERS", "VICTORY_LOOK",
            "VARCHAR(" + Look.MAX_NAME_LENGTH + ")", "LOOK");
        JDBCUtil.addColumn(
            conn, "PLAYERS", "WANTED_LOOK",
            "VARCHAR(" + Look.MAX_NAME_LENGTH + ")", "VICTORY_LOOK");
    }

    @Override // documentation inherited
    protected void createTables ()
    {
	_ptable = new Table<Player>(
            Player.class, "PLAYERS", "PLAYER_ID", true);
    }

    /** Helper function for {@link #spendScrip} and {@link #grantScrip}. */
    protected void updateScrip (String where, int amount, String type)
        throws PersistenceException
    {
        if (amount <= 0) {
            throw new PersistenceException(
                "Illegal scrip " + type + " [where=" + where +
                ", amount=" + amount + "]");
        }

        String action = type.equals("grant") ? "+" : "-";
        String query = "update PLAYERS set SCRIP = SCRIP " + action + " " +
            amount + " where " + where;
        int mods = update(query);
        if (mods == 0) {
            throw new PersistenceException(
                "Scrip " + type + " modified zero rows [where=" + where +
                ", amount=" + amount + "]");
        } else if (mods > 1) {
            log.warning("Scrip " + type + " modified multiple rows " +
                        "[where=" + where + ", amount=" + amount +
                        ", mods=" + mods + "].");
        }
    }

    protected Table<Player> _ptable;
    protected FieldMask _byNameMask;
}
