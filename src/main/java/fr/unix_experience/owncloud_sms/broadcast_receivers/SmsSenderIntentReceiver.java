package fr.unix_experience.owncloud_sms.broadcast_receivers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;

import org.json.JSONException;

import java.util.List;

import fr.unix_experience.owncloud_sms.engine.ASyncSMSSync;
import fr.unix_experience.owncloud_sms.engine.OCSMSOwnCloudClient;
import fr.unix_experience.owncloud_sms.providers.SmsSendStackProvider;
import ncsmsgo.SmsMessage;

public class SmsSenderIntentReceiver  extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getStringExtra("sms") != null){
            SmsMessage message = null;
            try {
                Account account = null;
                for (Account account1: AccountManager.get(context).getAccountsByType(intent.getStringExtra("account_type"))){
                    if(account1.name.equals(intent.getStringExtra("account_name"))){
                        account = account1;
                        break;
                    }
                }
                if(account == null){
                    Log.d("smsdebug","account null");
                    return;
                }

                OCSMSOwnCloudClient client = new OCSMSOwnCloudClient(context, account);
                message = SmsSendStackProvider.smsMessageFromString(intent.getStringExtra("sms"));

                SmsSendStackProvider stackProvider = SmsSendStackProvider.getInstance(context);
                stackProvider.setSent(message, 3);

                client.deleteMessage(message.getAddress(), message.getDate());
                new ASyncSMSSync.SyncTask(context, null).execute();

            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }
}
