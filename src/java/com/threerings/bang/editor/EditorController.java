//
// $Id$

package com.threerings.bang.editor;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import com.samskivert.util.IntListUtil;
import com.samskivert.util.ListUtil;
import com.samskivert.util.StringUtil;

import com.threerings.util.MessageBundle;

import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.SetListener;

import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.PlaceConfig;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import com.threerings.parlor.game.client.GameController;

import com.threerings.bang.game.data.BangBoard;
import com.threerings.bang.game.data.BangObject;
import com.threerings.bang.game.data.PieceDSet;
import com.threerings.bang.game.data.Terrain;
import com.threerings.bang.game.data.piece.Marker;
import com.threerings.bang.game.data.piece.Piece;
import com.threerings.bang.game.data.piece.PieceCodes;
import com.threerings.bang.game.data.piece.Track;
import com.threerings.bang.server.persist.BoardRecord;

import static com.threerings.bang.Log.log;

/**
 * Handles the logic and flow for the Bang! board editor.
 */
public class EditorController extends GameController
    implements PieceCodes
{
    /** Requests that we terminate the editor. */
    public static final String EXIT = "Exit";

    /** Instructs us to add the specified piece. */
    public static final String ADD_PIECE = "AddPiece";

    /** Instructs us to remove the specified piece. */
    public static final String REMOVE_PIECE = "RemovePiece";

    /** Instructs us to lay a piece of track at the specified tile coords. */
    public static final String LAY_TRACK = "LayTrack";
    
    /** Instructs us to remove any piece of track at the specified coords. */
    public static final String REMOVE_TRACK = "RemoveTrack";
    
    /** Instructs us to start a new board. */
    public static final String NEW_BOARD = "NewBoard";
    
    /** Instructs us to load a new board. */
    public static final String LOAD_BOARD = "LoadBoard";

    /** Instructs us to save the current board. */
    public static final String SAVE_BOARD = "SaveBoard";

    /** Instructs us to import a heightfield. */
    public static final String IMPORT_HEIGHTFIELD = "ImportHeightfield";
    
    /** Instructs us to export a heightfield. */
    public static final String EXPORT_HEIGHTFIELD = "ExportHeightfield";
    
    /** Instructs us to bring up the water properties dialog. */
    public static final String EDIT_WATER = "EditWater";
    
    /** Instructs us to bring up the light properties dialog. */
    public static final String EDIT_LIGHT = "EditLight";
    
    /** Instructs us to bring up the board size dialog. */
    public static final String EDIT_BOARD_SIZE = "EditBoardSize";
    
    /** Instructs us to toggle wireframe rendering. */
    public static final String TOGGLE_WIREFRAME = "ToggleWireframe";
    
    /** Instructs us to toggle the tile grid. */
    public static final String TOGGLE_GRID = "ToggleGrid";
    
    /** Instructs us to toggle the impassable tile highlights. */
    public static final String TOGGLE_HIGHLIGHTS = "ToggleHighlights";
    
    /** Instructs us to select one of the tools. */
    public static final String SELECT_TOOL = "SelectTool";
    
    /** Handles a request to exit the editor. Generated by the {@link
     * #EXIT} command. */
    public void handleExit (Object source)
    {
        // TODO: warn about an unsaved board
        System.exit(0);
    }

    /** Handles a request to add the specified piece to the board.
     * Generated by the {@link #ADD_PIECE} command. */
    public void handleAddPiece (Object source, Piece piece)
    {
        _bangobj.addToPieces(piece);
    }

    /** Handles a request to create a new piece and add it to the board.
     * Generated by the {@link #REMOVE_PIECE} command. */
    public void handleRemovePiece (Object source, Integer key)
    {
        _bangobj.removeFromPieces(key);
    }

    /** Handles a request to lay a piece of track at the specified tile coords.
     * Generated by the {@link #ADD_PIECE} command. */
    public void handleLayTrack (Object source, Point tcoords)
    {
        Track track = getTrack(tcoords.x, tcoords.y);
        if (track == null) {
            track = new Track();
            track.position(tcoords.x, tcoords.y);
            _bangobj.addToPieces(track);
        }
        
        // update the piece and its neighbors, if any
        updateTrack(track);
        updateTrack(getTrack(tcoords.x - 1, tcoords.y));
        updateTrack(getTrack(tcoords.x + 1, tcoords.y));
        updateTrack(getTrack(tcoords.x, tcoords.y - 1));
        updateTrack(getTrack(tcoords.x, tcoords.y + 1));
    }
    
    /** Handles a request to remove any piece of track at the specified coords.
     * Generated by the {@link #REMOVE_PIECE} command. */
    public void handleRemoveTrack (Object source, Point tcoords)
    {
        Track track = getTrack(tcoords.x, tcoords.y);
        if (track != null) {
            _bangobj.removeFromPieces(track.getKey());
        }
        
        // update the neighbors, if any
        updateTrack(getTrack(tcoords.x - 1, tcoords.y));
        updateTrack(getTrack(tcoords.x + 1, tcoords.y));
        updateTrack(getTrack(tcoords.x, tcoords.y - 1));
        updateTrack(getTrack(tcoords.x, tcoords.y + 1));
    }
    
    /** Handles a request to create a new board.  Generated by the
     * {@link #NEW_BOARD} command. */
    public void handleNewBoard (Object source)
    {
        if (_newBoard == null) {
            _newBoard = new NewBoardDialog(_ctx, _panel);
        }
        _newBoard.fromBoard(_bangobj.board);
        _newBoard.setLocation(100, 100);
        _newBoard.setLocationRelativeTo(_ctx.getFrame());
        _newBoard.setVisible(true);
    }
    
    /** Handles a request to load the current board.  Generated by the
     * {@link #LOAD_BOARD} command. */
    public void handleLoadBoard (Object source)
    {
        if (_boardChooser == null) {
            _boardChooser = new JFileChooser(_board.getParent());
        }
        int rv = _boardChooser.showOpenDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }

        loadBoard(_boardChooser.getSelectedFile());
    }

    /** Handles a request to save the current board.  Generated by the
     * {@link #SAVE_BOARD} command. */
    public void handleSaveBoard (Object source)
    {
        if (_boardChooser == null) {
            _boardChooser = new JFileChooser(_board.getParent());
        }
        int rv = _boardChooser.showSaveDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            File board = _boardChooser.getSelectedFile();
            BoardRecord brec = new BoardRecord();
            _panel.info.toBoard(brec);
            brec.setData(_bangobj.board, _bangobj.getPieceArray());
            brec.save(board);
            _board = board;
            _ctx.setWindowTitle(_board.toString());
            _ctx.displayStatus(_msgs.get("m.saved", _board));

        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.save_error", ioe.getMessage()));
        }
    }

    /** Handles a request to import a heightfield.  Generated by the
     * {@link #IMPORT_HEIGHTFIELD} command. */
    public void handleImportHeightfield (Object source)
    {
        if (_imageChooser == null) {
            createImageChooser();
        }
        int rv = _imageChooser.showOpenDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        try {
            File heightfield = _imageChooser.getSelectedFile();
            _panel.view.setHeightfield(ImageIO.read(heightfield));
            _ctx.displayStatus(_msgs.get("m.imported", heightfield));
            
        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.import_error", ioe.getMessage()));
        }
    }
    
    /** Handles a request to export a heightfield.  Generated by the
     * {@link #EXPORT_HEIGHTFIELD} command. */
    public void handleExportHeightfield (Object source)
    {
        if (_imageChooser == null) {
            createImageChooser();
        }
        int rv = _imageChooser.showSaveDialog(_ctx.getFrame());
        if (rv != JFileChooser.APPROVE_OPTION) {
            return;
        }
        
        try {
            File heightfield = _imageChooser.getSelectedFile();
            String name = heightfield.getName(),
                suffix = name.substring(name.indexOf('.')+1);
            ImageIO.write(_panel.view.getHeightfieldImage(), suffix,
                heightfield);
            _ctx.displayStatus(_msgs.get("m.exported", heightfield));
            
        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.export_error", ioe.getMessage()));
        }
    }
    
    /** Handles a request to edit the water properties.  Generated by the
     * {@link #EDIT_WATER} command. */
    public void handleEditWater (Object source)
    {
        if (_water == null) {
            _water = new WaterDialog(_ctx, _panel);
        }
        _water.fromBoard(_bangobj.board);
        _water.setLocation(100, 100);
        _water.setLocationRelativeTo(_ctx.getFrame());
        _water.setVisible(true);
    }
    
    /** Handles a request to edit the light properties.  Generated by the
     * {@link #EDIT_LIGHT} command. */
    public void handleEditLight (Object source)
    {
        if (_light == null) {
            _light = new LightDialog(_ctx, _panel);
        }
        _light.fromBoard(_bangobj.board);
        _light.setLocation(100, 100);
        _light.setLocationRelativeTo(_ctx.getFrame());
        _light.setVisible(true);
    }
    
    /** Handles a request to edit the board size.  Generated by the
     * {@link #EDIT_BOARD_SIZE} command. */
    public void handleEditBoardSize (Object source)
    {
        if (_boardSize == null) {
            _boardSize = new BoardSizeDialog(_ctx, _panel);
        }
        _boardSize.fromBoard(_bangobj.board);
        _boardSize.setLocation(100, 100);
        _boardSize.setLocationRelativeTo(_ctx.getFrame());
        _boardSize.setVisible(true);
    }
    
    /** Handles a request to toggle wireframe rendering.  Generated by the
     * {@link #TOGGLE_WIREFRAME} command. */
    public void handleToggleWireframe (Object source)
    {
        _panel.view.toggleWireframe();
    }
    
    /** Handles a request to toggle the tile grid.  Generated by the
     * {@link #TOGGLE_GRID} command. */
    public void handleToggleGrid (Object source)
    {
        _panel.view.toggleGrid();
    }
    
    /** Handles a request to toggle the highlights.  Generated by the
     * {@link #TOGGLE_HIGHLIGHTS} command. */
    public void handleToggleHighlights (Object source)
    {
        _panel.view.toggleHighlights();
    }
    
    /** Handles a request to select a tool.  Generated by the
     * {@link #SELECT_TOOL} command. */
    public void handleSelectTool (Object source, String name)
    {
        _panel.tools.selectTool(name);
    }
    
    // documentation inherited
    public void init (CrowdContext ctx, PlaceConfig config)
    {
        super.init(ctx, config);
        _ctx = (EditorContext)ctx;
        _config = (EditorConfig)config;
        _msgs = _ctx.getMessageManager().getBundle("editor");
    }

    // documentation inherited
    public void willEnterPlace (PlaceObject plobj)
    {
        super.willEnterPlace(plobj);
        _bangobj = (BangObject)plobj;
        _bangobj.addListener(_pclistener);
    }

    protected void loadBoard (File board)
    {
        try {
            BoardRecord brec = new BoardRecord();
            brec.load(board);
            _bangobj.setBoard(brec.getBoard());
            Piece[] pieces = brec.getPieces();
            // reassign piece ids
            for (int ii = 0; ii < pieces.length; ii++) {
                pieces[ii].pieceId = (ii+1);
            }
            Piece.setNextPieceId(pieces.length);
            _bangobj.setPieces(new PieceDSet(pieces));
            _panel.view.refreshBoard();
            _panel.info.fromBoard(brec);
            updatePlayerCount();
            _board = board;
            _ctx.setWindowTitle(_board.toString());
            _ctx.displayStatus(_msgs.get("m.loaded", _board));

        } catch (IOException ioe) {
            _ctx.displayStatus(_msgs.get("m.load_error", ioe.getMessage()));
        }
    }

    /**
     * Creates and initializes the image file chooser.
     */
    protected void createImageChooser ()
    {
        _imageChooser = new JFileChooser(_board.getParent());
        _imageChooser.setFileFilter(new FileFilter() {
            public boolean accept (File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String name = f.getName(),
                    suffix = name.substring(name.lastIndexOf('.')+1);
                return ListUtil.contains(ImageIO.getReaderFormatNames(),
                    suffix) &&
                    ListUtil.contains(ImageIO.getWriterFormatNames(), suffix);
            }
            public String getDescription () {
                return _msgs.get("m.hf_images");
            }
        });
    }
    
    @Override // documentation inherited
    protected PlaceView createPlaceView (CrowdContext ctx)
    {
        _panel = new EditorPanel((EditorContext)ctx, this);
        return _panel;
    }

    @Override // documentation inherited
    protected void gameDidStart ()
    {
        super.gameDidStart();

        // our panel needs to do some game starting business
        _panel.startGame(_bangobj, _config);

        // load up any board specified on the command line
        if (EditorApp.appArgs.length > 0 &&
            !StringUtil.isBlank(EditorApp.appArgs[0])) {
            loadBoard(new File(EditorApp.appArgs[0]));
        }
    }

    @Override // documentation inherited
    protected void gameWillReset ()
    {
        super.gameWillReset();
        _panel.endGame();
    }

    @Override // documentation inherited
    protected void gameDidEnd ()
    {
        super.gameDidEnd();
        _panel.endGame();
    }

    protected void updatePlayerCount ()
    {
        int pcount = 0;
        for (Iterator iter = _bangobj.pieces.iterator(); iter.hasNext(); ) {
            if (Marker.isMarker((Piece)iter.next(), Marker.START)) {
                pcount++;
            }
        }
        _panel.info.updatePlayers(pcount);
    }

    /**
     * Sets the type and orientation of a piece of track based on the sides
     * on which it has neighboring pieces.  Silently ignores null tracks.
     */
    protected void updateTrack (Track track)
    {
        if (track == null) {
            return;
        }
        track = (Track)track.clone();
        int[] neighbors = // n, e, s, w
            new int[] { getTrack(track.x, track.y - 1) == null ? 0 : 1,
                getTrack(track.x + 1, track.y) == null ? 0 : 1,
                getTrack(track.x, track.y + 1) == null ? 0 : 1,
                getTrack(track.x - 1, track.y) == null ? 0 : 1 };
        int ncount = IntListUtil.sum(neighbors);
        
        // except for turns, the values of the types are all equal to the
        // number of neighbors
        if (ncount == 2 && neighbors[EAST] != neighbors[WEST]) {
            track.type = Track.TURN;
            
        } else {
            track.type = (byte)ncount;
        }
        
        // orientation doesn't matter for singletons and cross junctions
        if (track.type == Track.TERMINAL) {
            track.orientation = (short)IntListUtil.indexOf(neighbors, 1);
            
        } else if (track.type == Track.STRAIGHT) {
            track.orientation = (short)(neighbors[EAST] == 1 ? EAST : NORTH);
        
        } else if (track.type == Track.T_JUNCTION) {
            track.orientation = (short)IntListUtil.indexOf(neighbors, 0);
            
        } else if (track.type == Track.TURN) {
            track.orientation = (short)(neighbors[NORTH] == 1 ?
                (neighbors[EAST] == 1 ? NORTH : WEST) :
                (neighbors[EAST] == 1 ? EAST : SOUTH));
        }
        
        _bangobj.updatePieces(track);
    }
    
    /**
     * Returns the piece of track at the specified tile coordinates, or
     * <code>null</code> if there isn't one.
     */
    protected Track getTrack (int tx, int ty)
    {
        for (Iterator it = _bangobj.pieces.iterator(); it.hasNext(); ) {
            Piece piece = (Piece)it.next();
            if (piece.x == tx && piece.y == ty && piece instanceof Track) {
                return (Track)piece;
            }
        }
        return null;
    }
    
    /** Listens for piece additions and removals. */
    protected SetListener _pclistener = new SetListener() {
        public void entryAdded (EntryAddedEvent event) {
            updatePlayerCount();
        }
        public void entryUpdated (EntryUpdatedEvent event) {
        }
        public void entryRemoved (EntryRemovedEvent event) {
            updatePlayerCount();
        }
    };

    /** A casted reference to our context. */
    protected EditorContext _ctx;

    /** The configuration of this game. */
    protected EditorConfig _config;

    /** Used to translate messages. */
    protected MessageBundle _msgs;

    /** Contains our main user interface. */
    protected EditorPanel _panel;

    /** A casted reference to our game object. */
    protected BangObject _bangobj;

    /** The file chooser we use for loading and saving boards. */
    protected JFileChooser _boardChooser;

    /** The file chooser we use for importing and exporting images. */
    protected JFileChooser _imageChooser;
    
    /** The new board dialog. */
    protected NewBoardDialog _newBoard;
    
    /** The water properties dialog. */
    protected WaterDialog _water;
    
    /** The light properties dialog. */
    protected LightDialog _light;
    
    /** The board size dialog. */
    protected BoardSizeDialog _boardSize;
    
    /** A reference to the file associated with the board we're editing. */
    protected File _board = new File("");
}
