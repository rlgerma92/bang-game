//
// $Id$

package com.threerings.bang.game.data.effect;

import com.threerings.bang.game.client.BallisticShotHandler;
import com.threerings.bang.game.client.EffectHandler;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Piece;

/**
 * Communicates that a ballistic shot was fired from one piece to another.
 */
public class BallisticShotEffect extends ShotEffect
{
    public BallisticShotEffect (Piece shooter, Piece target, int damage,
                                String attackIcon, String defendIcon)
    {
        super(shooter, target, damage, attackIcon, defendIcon);
    }

    /** Constructor used when unserializing. */
    public BallisticShotEffect ()
    {
    }

    @Override // documentation inherited
    public boolean isDeflectable ()
    {
        return true;
    }
    
    @Override // documentation inherited
    public EffectHandler createHandler (BangObject bangobj)
    {
        return new BallisticShotHandler();
    }

    /**
     * Returns the type of shot fired, which could be a model or an effect.
     */
    public String getShotType ()
    {
        return "units/frontier_town/artillery/shell";
    }
    
    @Override // documentation inherited
    protected String getEffect ()
    {
        return EXPLODED;
    }
}
