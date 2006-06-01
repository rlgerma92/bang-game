//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.ArrayUtil;
import com.samskivert.util.IntIntMap;
import com.samskivert.util.IntListUtil;

import com.threerings.bang.data.Stat;
import com.threerings.bang.data.UnitConfig;
import com.threerings.bang.game.client.BallisticShotHandler;
import com.threerings.bang.game.client.InstantShotHandler;
import com.threerings.bang.game.client.EffectHandler;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.Unit;
import com.threerings.bang.game.util.PieceUtil;

import static com.threerings.bang.Log.log;

/**
 * Communicates that a shot was fired from one piece to another.
 */
public class ShotEffect extends Effect
{
    /** Indicates that the target was damaged by a normal shot. */
    public static final String DAMAGED = "bang";

    /** Indicates that the target was damaged by a ballistic shot. */
    public static final String EXPLODED = "exploded";
    
    /** We also rotate the shooter, thereby affecting it. */
    public static final String ROTATED = "rotated";
    
    /** Indicates that a shooter shot without moving. */
    public static final String SHOT_NOMOVE = "shot_nomove";

    /** A normal shot. */
    public static final int NORMAL = 0;

    /** A return fire shot. */
    public static final int RETURN_FIRE = 1;

    /** A collateral damage shot. */
    public static final int COLLATERAL_DAMAGE = 2;

    /** Maps shot types to animation identifiers. */
    public static final String[] SHOT_ACTIONS = {
        "shooting", "returning_fire", "collateral_damage"
    };

    /** The type of shot. */
    public short type = NORMAL;

    /** The piece id of the shooter. */
    public int shooterId;

    /** Used to update the last acted tick of the shooter if appropriate. */
    public short shooterLastActed = -1;

    /** The piece id of the target. */
    public int targetId;

    /** The new total damage to assign to the target. */
    public int newDamage;

    /** An adjusted last acted time to apply to the target. */
    public short targetLastActed = -1;

    /** The x coordinates of the path this shot takes before finally
     * arriving at its target (not including the starting coordinate). */
    public short[] xcoords;

    /** The y coordinates of the path this shot takes before finally
     * arriving at its target (not including the starting coordinate). */
    public short[] ycoords;

    /** When non-null, the piece ids of the units deflecting the shot at
     * each coordinate. */
    public short[] deflectorIds;
    
    /** A secondary effect to apply before the shot. */
    public Effect preShotEffect;
    
    /**
     * Constructor used when creating a new shot effect.
     *
     * @param damage the amount by which to increase the target's damage.
     * This will be capped and converted to an absolute value.
     */
    public ShotEffect (Piece shooter, Piece target, int damage)
    {
        shooterId = shooter.pieceId;
        setTarget(target, damage);
    }

    /** Constructor used when unserializing. */
    public ShotEffect ()
    {
    }

    /**
     * Configures this shot effect with a target and a damage amount. Any
     * previous target will be overridden and the new target's coordinates
     * will be added onto the shot's path.
     *
     * @param damage the amount by which to increase the target's damage.
     * This will be capped and converted to an absolute value.
     */
    public void setTarget (Piece target, int damage)
    {
        if (targetId > 0) {
            deflectorIds = append(deflectorIds, (short)targetId);
        }
        targetId = target.pieceId;
        newDamage = Math.min(100, target.damage + damage);
        xcoords = append(xcoords, target.x);
        ycoords = append(ycoords, target.y);
    }

    /**
     * Deflects this shot away from its original target, clearing out the
     * target information and appending the specified coordinates onto the
     * shot's path. It is assumed that there is no piece at those
     * coordinates.
     */
    public void deflectShot (short x, short y)
    {
        deflectorIds = append(deflectorIds, (short)targetId);
        targetId = -1;
        newDamage = 0;
        xcoords = append(xcoords, x);
        ycoords = append(ycoords, y);
    }

    @Override // documentation inherited
    public int[] getAffectedPieces ()
    {
        int[] pieces = new int[] { shooterId, targetId };
        if (preShotEffect != null) {
            pieces = concatenate(pieces, preShotEffect.getAffectedPieces());
        }
        if (deflectorIds != null) {
            for (short deflectorId : deflectorIds) {
                pieces = ArrayUtil.append(pieces, deflectorId);
            }
        }
        return pieces;
    }

    @Override // documentation inherited
    public int[] getWaitPieces ()
    {
        return (preShotEffect == null) ?
            NO_PIECES : preShotEffect.getWaitPieces();
    }
    
    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        if (targetId == -1) { // we were deflected into la la land, no problem
            return;
        }

        Piece target = bangobj.pieces.get(targetId);
        if (target != null) {
            // award a 20 damage point (2 game point) bonus for a kill
            int bonus = (newDamage == 100) ? 20 : 0;
            dammap.increment(target.owner, newDamage - target.damage + bonus);
            if (newDamage == 100) {
                preShotEffect = target.willDie(bangobj, shooterId);
            } else {
                Piece shooter = bangobj.pieces.get(shooterId);
                if (shooter != null) {
                    preShotEffect = shooter.willShoot(bangobj, target, this);
                } else {
                    log.warning("Shot effect missing shooter [id=" +
                        shooterId + "].");
                }
            }
            if (preShotEffect != null) {
                preShotEffect.prepare(bangobj, dammap);
            }
        } else {
            log.warning("Shot effect missing target [id=" + targetId + "].");
        }
    }

    /**
     * This is called on the client by the shot handler to apply any unit state
     * changes needed <em>prior</em> to the shot animation. The normal effect
     * application takes place <em>after</em> the shot has been animated.
     */
    public void preapply (BangObject bangobj, Observer obs)
    {
        // rotate the shooter to face the target
        Unit shooter = (Unit)bangobj.pieces.get(shooterId);
        reportEffect(obs, shooter, ROTATED);
        
        // update the shooter's last acted if necessary
        if (shooter != null && shooterLastActed != -1 &&
            shooter.lastActed != shooterLastActed) {
            shooter.lastActed = shooterLastActed;
            reportEffect(obs, shooter, SHOT_NOMOVE);
            shooterLastActed = -1; // avoid doing this again in apply()
        }
    }

    @Override // documentation inherited
    public boolean apply (BangObject bangobj, Observer obs)
    {
        // apply any secondary pre-shot effect
        if (preShotEffect != null) {
            preShotEffect.apply(bangobj, obs);
        }
        
        // update the shooter's last acted if necessary
        Unit shooter = (Unit)bangobj.pieces.get(shooterId);
        if (shooter == null) {
            log.warning("Missing shooter " + this + ".");
            return false;
        }
        if (shooterLastActed != -1 && shooter.lastActed != shooterLastActed) {
            shooter.lastActed = shooterLastActed;
            reportEffect(obs, shooter, SHOT_NOMOVE);
        }

        // if we were deflected into la la land, we can stop here
        if (targetId == -1) {
            return true;
        }
        Piece target = (Piece)bangobj.pieces.get(targetId);
        if (target == null) {
            log.warning("Missing shot target " + this + ".");
            return false;
        }

        // if we have a new last acted to assign to the target, do that
        if (targetLastActed != -1) {
            target.lastActed = targetLastActed;
        }

        // finally do the damage
        String effect = shooter.getConfig().mode == UnitConfig.Mode.RANGE ?
            EXPLODED : DAMAGED;
        return damage(bangobj, obs, shooter.owner, target, newDamage, effect);
    }
    
    @Override // documentation inherited
    public EffectHandler createHandler (BangObject bangobj)
    {
        Unit shooter = (Unit)bangobj.pieces.get(shooterId);
        if (shooter == null) {
            log.warning("Missing shooter for shot effect! " +
                        "[effect=" + this + "].");
            return null;
        }
        if (shooter.getConfig().mode == UnitConfig.Mode.RANGE) {
            return new BallisticShotHandler();
        } else {
            return new InstantShotHandler();
        }
    }
    
    /** Helper function for setting targets. */
    protected short[] append (short[] array, short value)
    {
        short[] narray;
        if (array == null) {
            narray = new short[] { value };
        } else {
            narray = new short[array.length+1];
            System.arraycopy(array, 0, narray, 0, array.length);
            narray[array.length] = value;
        }
        return narray;
    }
}
