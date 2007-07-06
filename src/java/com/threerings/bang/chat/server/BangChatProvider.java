//
// $Id$

package com.threerings.bang.chat.server;

import com.threerings.crowd.chat.data.UserMessage;
import com.threerings.crowd.chat.server.ChatProvider;
import com.threerings.crowd.data.BodyObject;

import com.threerings.bang.avatar.data.Look;
import com.threerings.bang.chat.data.PlayerMessage;
import com.threerings.bang.data.AvatarInfo;
import com.threerings.bang.data.PlayerObject;
import com.threerings.bang.data.StatType;
import com.threerings.bang.server.BangServer;

/**
 * Extends the normal {@link ChatProvider} so that we can avatar information
 * along with our tells.
 */
public class BangChatProvider extends ChatProvider
{
    @Override // documentation inherited
    public void deliverTell (BodyObject target, UserMessage message)
    {
        PlayerObject user = (PlayerObject)target;
        user.stats.incrementStat(StatType.CHAT_RECEIVED, 1);

        user = (PlayerObject)BangServer.lookupBody(message.speaker);
        if (user != null) {
            user.stats.incrementStat(StatType.CHAT_SENT, 1);
        }

        super.deliverTell(target, message);
    }

    @Override // documentation inherited
    protected UserMessage createTellMessage (BodyObject source, String message)
    {
        PlayerObject player = (PlayerObject)source;
        AvatarInfo avatar = player.getLook(Look.Pose.DEFAULT).getAvatar(player);
        return new PlayerMessage(player.handle, avatar, message);
    }
}
