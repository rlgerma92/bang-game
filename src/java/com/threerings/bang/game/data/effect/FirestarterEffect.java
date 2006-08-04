//
// $Id$

package com.threerings.bang.game.data.effect;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Influence;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.Unit;

/**
 * An effect that causes any piece being shot to suffer damage each tick.
 */
public class FirestarterEffect extends SetInfluenceEffect
{
    @Override // documentation inherited
    protected Influence createInfluence (final Unit target)
    {
        return new Influence() {
            public String getName () {
                return "firestarter";
            }
            public Effect[] willShoot (
                    BangObject bangobj, final Piece target, ShotEffect shot)
            {
                return new Effect[] { new OnFireEffect(target) };
            }
        };
    }

    @Override // documentation inherited
    protected String getEffectName ()
    {
        return "firestarter";
    }
}
