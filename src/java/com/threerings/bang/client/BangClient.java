//
// $Id: BangClient.java 3283 2004-12-22 19:23:00Z ray $

package com.threerings.bang.client;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;

import com.jmex.bui.BWindow;

import com.samskivert.util.Config;
import com.samskivert.util.RunQueue;
import com.threerings.util.Name;

import com.threerings.cast.CharacterManager;
import com.threerings.cast.bundle.BundledComponentRepository;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService.ConfirmListener;
import com.threerings.presents.client.SessionObserver;

import com.threerings.crowd.chat.client.ChatDirector;
import com.threerings.crowd.client.PlaceView;

import com.threerings.parlor.game.data.GameAI;

import com.threerings.bang.avatar.client.CreateAvatarView;
import com.threerings.bang.avatar.data.AvatarCodes;
import com.threerings.bang.avatar.util.AvatarLogic;
import com.threerings.bang.ranch.client.FirstBigShotView;

import com.threerings.bang.game.client.BangView;
import com.threerings.bang.game.client.effect.ParticleFactory;
import com.threerings.bang.game.data.BangConfig;
import com.threerings.bang.game.data.TutorialConfig;
import com.threerings.bang.game.util.TutorialUtil;

import com.threerings.bang.data.BigShotItem;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.util.BangContext;

import static com.threerings.bang.Log.log;

/**
 * Takes care of instantiating all of the proper managers and loading up
 * all of the necessary configuration and getting the client bootstrapped.
 */
public class BangClient extends BasicClient
    implements SessionObserver
{
    /**
     * Initializes a new client and provides it with a frame in which to
     * display everything.
     */
    public void init (BangApp app)
    {
        _ctx = new BangContextImpl();
        initClient(_ctx, app, app);

        // listen for logon
        _client.addClientObserver(this);

        // create and display the logon view
        displayLogon();

        // and start unpacking our resources
        initResources(_lview);
    }

    /**
     * Returns a reference to the context in effect for this client. This
     * reference is valid for the lifetime of the application.
     */
    public BangContext getContext ()
    {
        return _ctx;
    }

    /**
     * Potentially shows the next phase of the client introduction and
     * tutorial. This is called after first logging on and then at the
     * completion of each phase of the intro and tutorial.
     */
    public void checkShowIntro ()
    {
        PlayerObject user = _ctx.getUserObject();

        // if this player does not have a name, it's their first time, so pop
        // up the create avatar view
        if (user.handle == null) {
            CreateAvatarView cav = new CreateAvatarView(_ctx);
            _ctx.getRootNode().addWindow(cav);
            cav.pack();
            cav.center();
            return;
        }

        // if they have no big shots then they need the intro for those
        if (!user.hasBigShot()) {
            FirstBigShotView fbsv = new FirstBigShotView(_ctx);
            _ctx.getRootNode().addWindow(fbsv);
            fbsv.pack(600, -1);
            fbsv.center();
            return;
        }

        // otherwise, display the town view
        _tview = new TownView(_ctx);
        _ctx.getRootNode().addWindow(_tview);
    }

    // documentation inherited from interface SessionObserver
    public void clientDidLogon (Client client)
    {
        // remove the logon display
        clearLogon();

        // we potentially jump right into a game when developing
        BangConfig config = null;
        if ("tutorial".equals(System.getProperty("test"))) {
            config = new BangConfig();
            TutorialConfig tconfig =
                TutorialUtil.loadTutorial(_rsrcmgr, "controls");
            config.players = new Name[] {
                _ctx.getUserObject().getVisibleName(),
                new Name("Larry") /*, new Name("Moe")*/ };
            config.ais = new GameAI[] {
                null, new GameAI(1, 50) /*, new GameAI(0, 50)*/ };
            config.scenarios = new String[] { tconfig.ident };
            config.tutorial = true;
            config.board = tconfig.board;

        } else if (System.getProperty("test") != null) {
            config = new BangConfig();
            config.players = new Name[] {
                _ctx.getUserObject().getVisibleName(),
                new Name("Larry") /*, new Name("Moe")*/ };
            config.ais = new GameAI[] {
                null, new GameAI(1, 50) /*, new GameAI(0, 50)*/ };
            config.scenarios = new String[] { "cj" };
            config.board = System.getProperty("board");
        }

        if (config != null) {
            ConfirmListener cl = new ConfirmListener() {
                public void requestProcessed () {
                }
                public void requestFailed (String reason) {
                    log.warning("Failed to create game: " + reason);
                }
            };
            _ctx.getParlorDirector().startSolitaire(config, cl);
            return;
        }

        // start up the introduction process, if appropriate, or if no intro is
        // needed this will show the town view
        checkShowIntro();
    }

    // documentation inherited from interface SessionObserver
    public void clientObjectDidChange (Client client)
    {
        // nada
    }

    // documentation inherited from interface SessionObserver
    public void clientDidLogoff (Client client)
    {
        System.exit(0);
    }

    @Override // documentation inherited
    protected void createContextServices (RunQueue rqueue)
    {
        super.createContextServices(rqueue);

        // create our custom directors
        _chatdir = new BangChatDirector(_ctx);

        // warm up the particle factory
        ParticleFactory.warmup(_ctx);
    }

    @Override // documentation inherited
    protected void postResourcesInit ()
    {
        super.postResourcesInit();

        try {
            _charmgr = new CharacterManager(
                _imgmgr, new BundledComponentRepository(
                    _rsrcmgr, _imgmgr, AvatarCodes.AVATAR_RSRC_SET));
            _alogic = new AvatarLogic(
                _rsrcmgr, _charmgr.getComponentRepository());

        } catch (IOException ioe) {
            // TODO: report to the client
            log.log(Level.WARNING, "Initialization failed.", ioe);
        }
    }

    protected void displayLogon ()
    {
        _lview = new LogonView(_ctx);
        _ctx.getRootNode().addWindow(_lview);
        _lview.pack();
        _lview.center();
    }

    protected void clearLogon ()
    {
        _ctx.getRootNode().removeWindow(_lview);
        _lview = null;
    }

    /** The context implementation. This provides access to all of the
     * objects and services that are needed by the operating client. */
    protected class BangContextImpl extends BasicContextImpl
        implements BangContext
    {
        /** Apparently the default constructor has default access, rather
         * than protected access, even though this class is declared to be
         * protected. Why, I don't know, but we need to be able to extend
         * this class elsewhere, so we need this. */
        protected BangContextImpl () {
        }

        public Config getConfig () {
            return _config;
        }

        public ChatDirector getChatDirector () {
            return _chatdir;
        }

        public void setPlaceView (PlaceView view) {
            if (_pview != null) {
                _ctx.getRootNode().removeWindow(_pview);
            } else if (_tview != null) {
                _ctx.getRootNode().removeWindow(_tview);
            }

            // wire a status view to this place view (show by pressing esc);
            // the window must be modal prior to adding it to the hierarchy to
            // ensure that it is a default event target (and hears the escape
            // key pressed event)
            _pview = (BWindow)view;
            _pview.setModal(true);
            if (!(_pview instanceof BangView)) {
                new StatusView(_ctx).bind(_pview);
            }

            // now we can add the window to the hierarchy
            _ctx.getRootNode().addWindow(_pview);

            // size the view to fill the display
            _pview.setBounds(0, 0, _ctx.getDisplay().getWidth(),
                             _ctx.getDisplay().getHeight());
        }

        public void clearPlaceView (PlaceView view) {
            if (_pview != view) {
                log.warning("Requested to clear non-current place view " +
                            "[have=" + _pview + ", got=" + view + "].");
                // try to cope
                if (_pview != null) {
                    _ctx.getRootNode().removeWindow(_pview);
                }
            }
            _ctx.getRootNode().removeWindow((BWindow)view);
            _pview = null;
            _ctx.getRootNode().addWindow(_tview);
        }

        public BangClient getBangClient() {
            return BangClient.this;
        }

        public PlayerObject getUserObject () {
            return (PlayerObject)getClient().getClientObject();
        }

        public CharacterManager getCharacterManager () {
            return _charmgr;
        }

        public AvatarLogic getAvatarLogic () {
            return _alogic;
        }
    }

    protected BangContextImpl _ctx;
    protected Config _config = new Config("bang");

    protected BangChatDirector _chatdir;
    protected CharacterManager _charmgr;
    protected AvatarLogic _alogic;

    protected BWindow _pview;
    protected LogonView _lview;
    protected TownView _tview;
}
