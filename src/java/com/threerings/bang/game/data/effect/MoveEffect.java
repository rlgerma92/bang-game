//
// $Id$

package com.threerings.bang.game.data.effect;

import com.samskivert.util.IntIntMap;

import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.piece.Piece;

import static com.threerings.bang.Log.log;

/**
 * An effect used when a unit moves.
 */
public class MoveEffect extends Effect
{
    /** The identifier of the piece that moved. */
    public int pieceId;

    /** The new last acted time to assign to the piece. */
    public short newLastActed;

    /** The new x location of the piece. */
    public short nx;

    /** The new y location of the piece. */
    public short ny;

    @Override // documentation inherited
    public void init (Piece piece)
    {
        pieceId = piece.pieceId;
    }

    @Override // documentation inherited
    public int[] getAffectedPieces ()
    {
        return new int[] { pieceId };
    }

    @Override // documentation inherited
    public void prepare (BangObject bangobj, IntIntMap dammap)
    {
        newLastActed = bangobj.tick;
    }

    @Override // documentation inherited
    public boolean apply (BangObject bangobj, Observer obs)
    {
        Piece piece = bangobj.pieces.get(pieceId);
        if (piece == null) {
            log.warning("Missing target for move effect [id=" + pieceId + "].");
            return false;
        }

        piece.lastActed = newLastActed;
        if (piece.x != nx || piece.y != ny) {
            moveAndReport(bangobj, piece, nx, ny, obs);
        } else {
            // we updated last acted, so we need to report something
            reportEffect(obs, piece, UPDATED);
        }
        return true;
    }
}
