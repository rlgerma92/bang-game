//
// $Id$

package com.threerings.bang.server.persist;

import com.samskivert.io.PersistenceException;

import com.threerings.presents.client.InvocationService;

import com.threerings.bang.server.BangServer;

/**
 * Handles a financial action undertaken on behalf of another peer node.
 */
public abstract class PeerFinancialAction extends FinancialAction
{
    @Override // documentation inherited
    public void start ()
    {
        BangServer.invoker.postUnit(this);
    }

    @Override // documentation inherited
    public void handleResult ()
    {
        if (_failmsg != null) {
            actionFailed(_failmsg);
        } else {
            actionCompleted();
        }
    }

    protected PeerFinancialAction (
        String coinAccount, int playerId, int scripCost, int coinCost,
        InvocationService.ConfirmListener listener)
    {
        super(scripCost, coinCost);
        _coinAccount = coinAccount;
        _playerId = playerId;
        _listener = listener;
    }

    @Override // documentation inherited
    protected String getCoinAccount ()
    {
        return _coinAccount;
    }

    @Override // documentation inherited
    protected void spendScrip (int scrip)
        throws PersistenceException
    {
        BangServer.playrepo.spendScrip(_playerId, scrip);
    }

    @Override // documentation inherited
    protected void grantScrip (int scrip)
        throws PersistenceException
    {
        BangServer.playrepo.grantScrip(_playerId, scrip);
    }

    @Override // documentation inherited
    protected void actionCompleted ()
    {
        _listener.requestProcessed();
    }

    @Override // documentation inherited
    protected void actionFailed (String cause)
    {
        _listener.requestFailed(cause);
    }

    @Override // documentation inherited
    protected void toString (StringBuffer buf)
    {
        buf.append("type=").append(getClass().getName());
        buf.append(", coinAccount=").append(_coinAccount);
        buf.append(", playerId=").append(_playerId);
        buf.append(", scrip=").append(_scripCost);
        buf.append(", coins=").append(_coinCost);
    }

    protected String _coinAccount;
    protected int _playerId;
    protected InvocationService.ConfirmListener _listener;
}
