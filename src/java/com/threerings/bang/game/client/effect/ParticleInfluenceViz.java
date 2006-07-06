//
// $Id$

package com.threerings.bang.game.client.effect;

import com.jme.scene.Spatial;

import com.threerings.bang.client.util.ResultAttacher;
import com.threerings.bang.game.client.sprite.PieceSprite;
import com.threerings.bang.util.BasicContext;

/**
 * An influence visualization consisting of a particle system.
 */
public class ParticleInfluenceViz extends InfluenceViz
{
    public ParticleInfluenceViz (String effect)
    {
        _effect = effect;
    }
    
    @Override // documentation inherited
    public void init (BasicContext ctx, PieceSprite target)
    {
        super.init(ctx, target);
        _ctx.loadEffect(_effect, new ResultAttacher<Spatial>(target) {
            public void requestCompleted (Spatial result) {
                super.requestCompleted(result);
                _particles = result;
            }
        });
    }
    
    @Override // documentation inherited
    public void destroy ()
    {
        if (_particles != null) {
            _target.detachChild(_particles);
        }
    }
    
    /** The name of the particle effect to use. */
    protected String _effect;
    
    /** The particle system node. */
    protected Spatial _particles;
}
