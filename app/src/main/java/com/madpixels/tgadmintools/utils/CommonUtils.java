package com.madpixels.tgadmintools.utils;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.madpixels.apphelpers.MyLog;
import com.madpixels.apphelpers.Utils;
import com.madpixels.tgadmintools.App;
import com.madpixels.tgadmintools.R;
import com.madpixels.tgadmintools.db.DBHelper;
import com.madpixels.tgadmintools.entities.ChatLogInfo;
import com.madpixels.tgadmintools.entities.ChatTask;
import com.madpixels.tgadmintools.entities.FormattedTagText;
import com.madpixels.tgadmintools.entities.LogEntity;
import com.madpixels.tgadmintools.helper.TelegramBot;
import com.madpixels.tgadmintools.helper.TgH;

import org.drinkless.td.libcore.telegram.Client;
import org.drinkless.td.libcore.telegram.TdApi;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by Snake on 08.01.2017.
 */

public class CommonUtils {
    /**
     * Format text helper with custom tags
     */

    public static String safeHtml(final String text, boolean useSafe){
        return useSafe? TextUtils.htmlEncode(text) : text;
    }

    public static FormattedTagText replaceCustomShortTags(String customText, @Nullable ChatTask task, TdApi.User user, int tryes, @Nullable TdApi.Chat chat,
                                                          boolean isHTML) {
        FormattedTagText formattedText = new FormattedTagText();
       // boolean isHTML = true;

        customText = customText.replace("%name%", safeHtml(user.firstName + " " + user.lastName, isHTML) );
        customText = customText.replace("%u_id%", user.id + "" );
        customText = customText.replace("%c_title%", chat != null ? safeHtml(chat.title, isHTML) : "");

        customText = customText.replace("%count%", tryes == -1 ? "-" : (tryes + 1) + "");
        if (task != null) {
            customText = customText.replace("%c_id%", task.chat_id + "");
            customText = customText.replace("%max%", task.mAllowCountPerUser + "");
            customText = customText.replace("%left%", tryes == -1 ? "-" : (task.mAllowCountPerUser - (tryes + 1)) + "");
        }else{
            customText = customText.replace("%max%", "-");
            customText = customText.replace("%left%", "-");
        }

        if (!user.username.isEmpty())
            customText = customText.replace("%username%", "@" + user.username );
        else if (customText.contains("%username%")) {
            int index = customText.indexOf("%username%");
            customText = customText.replace("%username%", safeHtml(user.firstName, isHTML) );
            formattedText.mention = new TdApi.MessageEntityMentionName(index, user.firstName.length(), user.id);
        }

        formattedText.resultText = customText;

        return formattedText;
    }

    /**
     * Send message as bot and check response for error
     */
    public static void sendMessageViaBot(final String token, final long chatId, final String text,
                                         final boolean isHtmlParse, final boolean isMarkdown) {
        new Thread() {
            @Override
            public void run() {
                TelegramBot bot = new TelegramBot(token);
                String json = isHtmlParse ? bot.sendMessageHtml(chatId + "", text) :
                        isMarkdown ? bot.sendMessageMarkdown(chatId + "", text) :
                                bot.sendMessage(chatId + "", text);
                try {
                    JSONObject jObj = new JSONObject(json);
                    if (jObj.optBoolean("ok") == false) {
                        int error_code = jObj.optInt("error_code");
                        String description = jObj.optString("description");
                        if (error_code == 400 && description.contains("chat not found"))
                            description += "\nPlease be sure that your Bot invited to the chat\n" +
                                    "with token: ***************" + token.substring(16);
                        new LogUtil().logBotError(error_code, description, chatId, text);
                    }
                } catch (JSONException e) {
                    new LogUtil().logBotError(-1, e.getMessage(), chatId, text);
                }
            }
        }.start();
    }

    public static String getBotForChatAlerts(long chatID) {
        ChatTask task = DBHelper.getInstance().getChatTask(chatID, ChatTask.TYPE.CHAT_BOT);
        if (task != null)
            return task.mText;
        return null;
    }

    public static void forwardLogEventToChat(long chatID, LogEntity log) {
        ChatLogInfo chatLogInfo = DBHelper.getInstance().getLogEventsToChat(chatID, true);
        if (chatLogInfo == null)
            return;

        String botToken = getBotForChatAlerts(chatID);
        if (botToken != null) {
            String title = "<b>" + TextUtils.htmlEncode(log.getTitle()) + "</b>\n";
            String text = title + TextUtils.htmlEncode(log.getLogText());
            sendMessageViaBot(botToken, chatLogInfo.chatLogID, text, true, false);
            return;
        }


        TdApi.InputMessageText msg = new TdApi.InputMessageText();
        String title = log.getTitle();
        msg.text = title + "\n" + log.getLogText();

        msg.entities = new TdApi.MessageEntity[1];
        msg.entities[0] = new TdApi.MessageEntityBold(0, title.length());

        TdApi.TLFunction f = new TdApi.SendMessage(chatLogInfo.chatLogID, 0, false, true, null, msg);
        TgH.send(f, new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.TLObject object) {
                MyLog.log(object.toString());
            }
        });
    }

    /****
     * /**
     *
     * @return first link in text
     * /
     * static String getLink(final String text) {
     * ArrayList<String> links = new ArrayList<String>(2);
     * //boolean match_images = false;
     * //text ="https://pp.vk.me/c7007/v7007762/11672/4rgHVPWfCdc.jpg;
     * //text = "http://i58.fastpic.ru/big/2014/0205/6f/b0258e8556e17a17ea90bc49d498f06f.jpg";
     * String regex = "\\(?\\b(ftp://http://|https://|www[.]|@)?[-A-Za-zА-Яа-я0-9+&@#/%?=~_()|!:,.;]*[-A-ZА-Яа-яa-z0-9+&@#/%=~_()|]";
     * //if(match_images)
     * //    regex+=".(jpg|png|jpeg|bmp|gif)"; // add jpg filter
     * <p>
     * Pattern p = Pattern.compile(regex);
     * Matcher m = p.matcher(text);
     * final boolean allowStickers = Sets.getBoolean(Const.ANTISPAM_ALLOW_STICKERS_LINKS, true);
     * final DBHelper db = DBHelper.getInstance();
     * while (m.find()) {
     * String urlStr = m.group();
     * if (urlStr.indexOf('.') == -1) continue;
     * if (urlStr.indexOf("//") == -1 && urlStr.indexOf(".com") == -1 && urlStr.indexOf(".ru") == -1
     * && urlStr.indexOf(".me") == -1
     * && urlStr.indexOf(".рф") == -1 && urlStr.indexOf(".ua") == -1 && urlStr.indexOf(".org") == -1
     * && !urlStr.startsWith("@"))
     * continue;
     * // MyLog.log("isLink exists: " + urlStr);
     * if (allowStickers && urlStr.contains("telegram.me/addstickers/")) continue;
     * if (!db.isLinkInWhiteList(urlStr))
     * return urlStr;
     * //char[] stringArray1 = urlStr.toCharArray();
     * <p>
     * // if (urlStr.startsWith("(") && urlStr.endsWith(")"))
     * // {
     * <p>
     * // char[] stringArray = urlStr.toCharArray();
     * <p>
     * // char[] newArray = new char[stringArray.length-2];
     * // System.arraycopy(stringArray, 1, newArray, 0, stringArray.length-2);
     * //  urlStr = new String(newArray);
     * // System.out.println("Finally Url ="+newArray.toString());
     * <p>
     * // }
     * //System.out.println("...Url..."+urlStr);
     * // links.add(urlStr);
     * }
     * //return links;
     * return null;
     * }
     */

    public static String tsToDate(long seconds) {
        return Utils.TimestampToDateFormat(seconds, App.getContext().getString(R.string.dateformat_full));
    }
}
