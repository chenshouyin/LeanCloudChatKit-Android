package cn.leanclud.imkit.handler;

import android.content.Context;
import android.content.Intent;

import com.avos.avoscloud.AVCallback;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.im.v2.AVIMClient;
import com.avos.avoscloud.im.v2.AVIMConversation;
import com.avos.avoscloud.im.v2.AVIMTypedMessage;
import com.avos.avoscloud.im.v2.AVIMTypedMessageHandler;
import com.avos.avoscloud.im.v2.messages.AVIMTextMessage;

import cn.leanclud.imkit.LCIMKit;
import cn.leanclud.imkit.LCIMUserProfile;
import cn.leanclud.imkit.R;
import cn.leanclud.imkit.cache.ConversationItemCache;
import cn.leanclud.imkit.cache.ProfileCache;
import cn.leanclud.imkit.event.LCIMIMTypeMessageEvent;
import cn.leanclud.imkit.utils.LCIMConstants;
import cn.leanclud.imkit.utils.LCIMNotificationUtils;
import de.greenrobot.event.EventBus;

/**
 * Created by zhangxiaobo on 15/4/20.
 *  AVIMTypedMessage 的 handler，socket 过来的 AVIMTypedMessage 都会通过此 handler 与应用交互
 *  需要应用主动调用 AVIMMessageManager.registerMessageHandler 来注册
 *  当然，自定义的消息也可以通过这种方式来处理
 */
public class LCIMMessageHandler extends AVIMTypedMessageHandler<AVIMTypedMessage> {

  private Context context;

  public LCIMMessageHandler(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onMessage(AVIMTypedMessage message, AVIMConversation conversation, AVIMClient client) {
    if (message == null || message.getMessageId() == null) {
//      LogUtils.d("may be SDK Bug, message or message id is null");
      return;
    }

    if (LCIMKit.getInstance().getCurrentUserId() == null) {
//      LogUtils.d("selfId is null, please call setupManagerWithUserId ");
      client.close(null);
    } else {
      if (!client.getClientId().equals(LCIMKit.getInstance().getCurrentUserId())) {
        client.close(null);
      } else {
        if (!message.getFrom().equals(client.getClientId())) {
          if (LCIMNotificationUtils.isShowNotification(conversation.getConversationId())) {
            sendNotification(message, conversation);
          }
          ConversationItemCache.getInstance().increaseUnreadCount(message.getConversationId());
          sendEvent(message, conversation);
        } else {
          ConversationItemCache.getInstance().insertConversation(message.getConversationId());
        }
      }
    }
  }

  @Override
  public void onMessageReceipt(AVIMTypedMessage message, AVIMConversation conversation, AVIMClient client) {
    super.onMessageReceipt(message, conversation, client);
  }

  /**
   * 发送消息到来的通知事件
   * @param message
   * @param conversation
   */
  private void sendEvent(AVIMTypedMessage message, AVIMConversation conversation) {
    LCIMIMTypeMessageEvent event = new LCIMIMTypeMessageEvent();
    event.message = message;
    event.conversation = conversation;
    EventBus.getDefault().post(event);
  }

  private void sendNotification(final AVIMTypedMessage message, final AVIMConversation conversation) {
    if (null != conversation && null != message) {
      final String notificationContent = message instanceof AVIMTextMessage ?
        ((AVIMTextMessage) message).getText() : context.getString(R.string.lcim_unspport_message_type);
      ProfileCache.getInstance().getCachedUser(message.getFrom(), new AVCallback<LCIMUserProfile>() {
        @Override
        protected void internalDone0(LCIMUserProfile userProfile, AVException e) {
          if (e != null) {
            e.printStackTrace();
          } else if (null != userProfile) {
            String title = userProfile.getUserName();
            Intent intent = new Intent();
            intent.setAction("com.avoscloud.chat.intent.clinotification");
            intent.putExtra(LCIMConstants.CONVERSATION_ID, conversation.getConversationId());
            intent.putExtra(LCIMConstants.PEER_ID, message.getFrom());
            LCIMNotificationUtils.showNotification(context, title, notificationContent, null, intent);
          }
        }
      });
    }
  }
}