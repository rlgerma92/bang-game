//
// $Id: BangClient.java 3283 2004-12-22 19:23:00Z ray $

package com.threerings.bang.client;

import com.jme.bui.BLookAndFeel;
import com.jme.bui.event.InputDispatcher;
import com.jme.input.InputHandler;
import com.jme.renderer.Camera;
import com.jme.renderer.Renderer;
import com.jme.scene.Node;
import com.jme.system.DisplaySystem;

import com.samskivert.util.Config;
import com.threerings.resource.ResourceManager;
import com.threerings.util.MessageManager;
import com.threerings.util.Name;

import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService.ConfirmListener;
import com.threerings.presents.client.SessionObserver;
import com.threerings.presents.dobj.DObjectManager;

import com.threerings.crowd.chat.client.ChatDirector;
import com.threerings.crowd.client.LocationDirector;
import com.threerings.crowd.client.OccupantDirector;
import com.threerings.crowd.client.PlaceView;

import com.threerings.parlor.client.ParlorDirector;
import com.threerings.parlor.game.data.GameAI;

import com.threerings.bang.data.BangConfig;
import com.threerings.bang.util.BangContext;

import static com.threerings.bang.Log.log;

/**
 * Takes care of instantiating all of the proper managers and loading up
 * all of the necessary configuration and getting the client bootstrapped.
 */
public class BangClient
    implements SessionObserver
{
    /**
     * Initializes a new client and provides it with a frame in which to
     * display everything.
     */
    public void init (BangApp app)
    {
        _app = app;

        // create our context
        _ctx = new BangContextImpl();

        // create the directors/managers/etc. provided by the context
        createContextServices(app);

        // listen for logon
        _client.addClientObserver(this);
    }

    /**
     * Returns a reference to the context in effect for this client. This
     * reference is valid for the lifetime of the application.
     */
    public BangContext getContext ()
    {
        return _ctx;
    }

    // documentation inherited from interface SessionObserver
    public void clientDidLogon (Client client)
    {
        // create a one player game of bang
        BangConfig config = new BangConfig();
        config.players = new Name[] {
            _client.getCredentials().getUsername(), new Name("AI") };
        config.ais = new GameAI[] { null, new GameAI(0, 50) };
        ConfirmListener cl = new ConfirmListener() {
            public void requestProcessed () {
            }
            public void requestFailed (String reason) {
                log.warning("Failed to create game: " + reason);
            }
        };
        _ctx.getParlorDirector().startSolitaire(config, cl);
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

    /**
     * Creates and initializes the various services that are provided by
     * the context. Derived classes that provide an extended context
     * should override this method and create their own extended
     * services. They should be sure to call
     * <code>super.createContextServices</code>.
     */
    protected void createContextServices (BangApp app)
    {
        // create the handles on our various services
        _client = new Client(null, app);

        // these manage local client resources
        _rsrcmgr = new ResourceManager("rsrc");
        _msgmgr = new MessageManager(MESSAGE_MANAGER_PREFIX);
        _lnf = BLookAndFeel.getDefaultLookAndFeel();
        _mcache = new ModelCache(_ctx);

        // these manage "online" state
        _locdir = new LocationDirector(_ctx);
        _occdir = new OccupantDirector(_ctx);
        _pardir = new ParlorDirector(_ctx);
        _chatdir = new ChatDirector(_ctx, _msgmgr, null);

        // warm up the explosion factory
        ExplosionFactory.warmup(_ctx);
    }

    /**
     * The context implementation. This provides access to all of the
     * objects and services that are needed by the operating client.
     */
    protected class BangContextImpl extends BangContext
    {
        /**
         * Apparently the default constructor has default access, rather
         * than protected access, even though this class is declared to be
         * protected. Why, I don't know, but we need to be able to extend
         * this class elsewhere, so we need this.
         */
        protected BangContextImpl () {
        }

        public Client getClient () {
            return _client;
        }

        public DObjectManager getDObjectManager () {
            return _client.getDObjectManager();
        }

        public Config getConfig () {
            return _config;
        }

        public LocationDirector getLocationDirector () {
            return _locdir;
        }

        public OccupantDirector getOccupantDirector () {
            return _occdir;
        }

        public ChatDirector getChatDirector () {
            return _chatdir;
        }

        public ParlorDirector getParlorDirector () {
            return _pardir;
        }

        public void setPlaceView (PlaceView view) {
            // TBD
        }

        public void clearPlaceView (PlaceView view) {
            // we'll just let the next place view replace our old one
        }

        public ResourceManager getResourceManager () {
            return _rsrcmgr;
        }

        public MessageManager getMessageManager () {
            return _msgmgr;
        }

        public ModelCache getModelCache () {
            return _mcache;
        }

        public DisplaySystem getDisplay () {
            return _app.getContext().getDisplay();
        }

        public Renderer getRenderer () {
            return _app.getContext().getRenderer();
        }

        public Camera getCamera () {
            return _app.getContext().getCamera();
        }

        public Node getGeometry () {
            return _app.getContext().getGeometry();
        }

        public Node getInterface () {
            return _app.getContext().getInterface();
        }

        public InputHandler getInputHandler () {
            return _app.getContext().getInputHandler();
        }

        public InputDispatcher getInputDispatcher () {
            return _app.getContext().getInputDispatcher();
        }

        public BLookAndFeel getLookAndFeel () {
            return _lnf;
        }
    }

    protected BangContext _ctx;
    protected BangApp _app;
    protected Config _config = new Config("bang");

    protected MessageManager _msgmgr;
    protected ResourceManager _rsrcmgr;
    protected BLookAndFeel _lnf;
    protected ModelCache _mcache;

    protected Client _client;
    protected LocationDirector _locdir;
    protected OccupantDirector _occdir;
    protected ChatDirector _chatdir;
    protected ParlorDirector _pardir;

    /** The prefix prepended to localization bundle names before looking
     * them up in the classpath. */
    protected static final String MESSAGE_MANAGER_PREFIX = "rsrc.i18n";
}
