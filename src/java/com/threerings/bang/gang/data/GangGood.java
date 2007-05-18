//
// $Id$

package com.threerings.bang.gang.data;

import com.threerings.bang.data.PlayerObject;

import com.threerings.bang.store.data.Good;

/**
 * A good that may be purchased in the gang store.
 */
public abstract class GangGood extends Good
{
    /** A constructor only used during serialization. */
    public GangGood ()
    {
    }

    /**
     * The coin cost for this good.
     */
    public int getCoinCost ()
    {
        return _coinCost;
    }

    @Override // documentation inherited
    public int getCoinCost (PlayerObject user)
    {
        return _coinCost;
    }

    /**
     * Returns the cost of this good in aces. This is in addition to the scrip and coin costs.
     */
    public int getAceCost ()
    {
        return _aceCost;
    }

    /**
     * Indicates that this good is available to the specified gang.
     */
    public abstract boolean isAvailable (GangObject gang);

    @Override // from Good
    public boolean isAvailable (PlayerObject player)
    {
        return false;
    }

    /** Creates a gang good of the specified type. */
    protected GangGood (String type, int scripCost, int coinCost, int aceCost)
    {
        super(type, null, scripCost, coinCost, 0);
        _aceCost = aceCost;
    }

    protected int _aceCost;
}
