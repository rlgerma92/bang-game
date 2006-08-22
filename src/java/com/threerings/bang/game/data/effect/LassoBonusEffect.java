//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.IntIntMap;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.GameCodes;
import com.threerings.bang.game.data.card.Card;
import com.threerings.bang.game.data.piece.Bonus;
import com.threerings.bang.game.data.piece.Piece;

import static com.threerings.bang.Log.log;

/**
 * Represents the act of turning a bonus on the board into a card.
 */
public class LassoBonusEffect extends BonusEffect
{
    /** The identifier for the type of effect that we produce. */
    public static final String LASSOED_BONUS = "frontier_town/lasso";

    /** The card that we're granting. */
    public Card card;
    
    public LassoBonusEffect ()
    {
    }

    public LassoBonusEffect (int player, int x, int y)
    {
        _player = player;
        _x = x;
        _y = y;
    }

    @Override // documentation inherited
    public int[] getAffectedPieces ()
    {
        return new int[0];
    }

    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        super.prepare(bangobj, dammap);

        // make sure our player has room for another card
        if (bangobj.countPlayerCards(_player) >= GameCodes.MAX_CARDS) {
            log.warning("No soup four you! " + _player + ".");
            return;
        }

        // find the bonus
        Bonus bonus = null;
        for (Piece piece : bangobj.pieces) {
            if (piece instanceof Bonus && piece.intersects(_x, _y)) {
                bonus = (Bonus)piece;
                break;
            }
        }
        if (bonus == null) {
            log.warning("Couldn't find bonus for lasso effect [pidx=" +
                _player + ", x=" + _x + ", y=" + _y + "].");
            return;
        }
        String ctype = bonus.getConfig().cardType;
        if (ctype == null) {
            log.warning("Tried to lasso bonus without corresponding card " +
                "[pidx=" + _player + ", bonus=" + bonus + "].");
            return;
        }
        bonusId = bonus.pieceId;

        // grant the corresponding card
        if (ctype.equals("__random__")) {
            ctype = Card.selectRandomCard(bangobj.townId,
                bangobj.pdata[_player].pointFactor, bangobj.scenario);
        }
        card = Card.newCard(ctype);
        card.init(bangobj, _player);
    }

    @Override // documentation inherited
    public boolean isApplicable ()
    {
        return card != null;
    }

    @Override // documentation inherited
    public boolean apply (BangObject bangobj, Observer obs)
    {
        super.apply(bangobj, obs);
        addAndReport(bangobj, card, obs);
        return true;
    }
    
    @Override // documentation inherited
    protected String getActivatedEffect ()
    {
        return LASSOED_BONUS;
    }
    
    /** The source and target of the lasso. */
    protected transient int _player, _x, _y;
}
