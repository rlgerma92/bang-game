//
// $Id$

package com.threerings.bang.game.server;

import com.threerings.presents.data.ClientObject;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationProvider;

import com.threerings.bang.game.client.BangService;

/**
 * Defines the server side of the {@link BangService}.
 */
public interface BangProvider extends InvocationProvider
{
    /** Handles a {@link BangService#selectStarters} request. */
    public void selectStarters (
        ClientObject caller, int bigShotId, int[] cardIds);

    /** Handles a {@link BangService#purchaseUnits} request. */
    public void purchaseUnits (ClientObject caller, String[] units);

    /** Handles a {@link BangService#readyToPlay} request. */
    public void move (ClientObject caller, int pieceId, short x, short y,
                      int targetId, BangService.InvocationListener il)
        throws InvocationException;

    /** Handles a {@link BangService#playCard} request. */
    public void playCard (ClientObject caller, int cardId, short x, short y);
}
