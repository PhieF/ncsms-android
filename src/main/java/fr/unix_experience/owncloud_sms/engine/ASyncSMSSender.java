package fr.unix_experience.owncloud_sms.engine;

import android.accounts.Account;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.List;

import fr.unix_experience.owncloud_sms.activities.remote_account.RestoreMessagesActivity;
import fr.unix_experience.owncloud_sms.broadcast_receivers.SmsSenderIntentReceiver;
import fr.unix_experience.owncloud_sms.enums.MailboxID;
import fr.unix_experience.owncloud_sms.providers.SmsDataProvider;
import fr.unix_experience.owncloud_sms.providers.SmsSendStackProvider;
import ncsmsgo.SmsMessage;
import ncsmsgo.SmsMessagesResponse;

/*
 *  Copyright (c) 2014-2016, Loic Blot <loic.blot@unix-experience.fr>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public interface ASyncSMSSender {
	class SMSSenderSyncTask extends AsyncTask<Void, Integer, Void> {
		private final RestoreMessagesActivity _context;
		private final Account _account;

		public SMSSenderSyncTask(RestoreMessagesActivity context, Account account) {
			_context = context;
			_account = account;
		}

		@Override
		protected Void doInBackground(Void... params) {
			// This feature is only available for Android 4.4 and greater
			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
				return null;
			}

			if (!new ConnectivityMonitor(_context).isValid()) {
				Log.e(ASyncSMSSender.TAG, "Restore connectivity problems, aborting");
				return null;
			}
			Log.i(ASyncSMSSender.TAG, "Starting background recovery");
			Long start = (long) 0;

			OCSMSOwnCloudClient client = new OCSMSOwnCloudClient(_context, _account);
			SmsDataProvider smsDataProvider = new SmsDataProvider(_context);
			SmsMessagesResponse obj = client.retrieveSomeMessages(start, 500);
			if (obj == null) {
				Log.i(ASyncSMSSender.TAG, "Retrieved returns failure");
				return null;
			}
			SmsSendStackProvider stackProvider = SmsSendStackProvider.getInstance(_context);

			Integer nb = 0;
			while ((obj != null) && (obj.getLastID() != start)) {
				Log.i(TAG, "Retrieving messages from " + Long.toString(start)
						+ " to " + Long.toString(obj.getLastID()));
				SmsMessage message;
				while ((message = obj.getNextMessage()) != null) {
					int mbid = (int) message.getMailbox();
					// Ignore invalid mailbox
					if (mbid > MailboxID.ALL.getId()) {
						Log.e(ASyncSMSSender.TAG, "Invalid mailbox found: " + mbid);
						continue;
					}


					String address = message.getAddress();
					String body = message.getMessage();
					int type = (int) message.getType();
					if (address.isEmpty() || body.isEmpty()) {
						Log.e(ASyncSMSSender.TAG, "Invalid SMS message found: " + message.toString());
						continue;
					}

					MailboxID mailbox_id = MailboxID.fromInt(mbid);

					String date = Long.toString(message.getDate());

					// Ignore already existing messages
					if (smsDataProvider.messageExists(address, body, date, mailbox_id)) {
						publishProgress(nb);
						continue;
					}

					if(message.getSent() == 0){
						stackProvider.addMessage(message);
					}
					nb++;
					if ((nb % 10) == 0) {
						publishProgress(nb);
					}
				}

				start = obj.getLastID();

				if (!new ConnectivityMonitor(_context).isValid()) {
					Log.e(ASyncSMSSender.TAG, "Restore connectivity problems, aborting");
					return null;
				}
				obj = client.retrieveSomeMessages(start, 500);
			}

			// Force this refresh to fix dates
			_context.getContentResolver().delete(Uri.parse("content://sms/conversations/-1"),
					null, null);

			publishProgress(nb);

			Log.i(ASyncSMSSender.TAG, "Finishing background recovery");
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			_context.onProgressUpdate(values[0]);
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			super.onPostExecute(aVoid);
			_context.onRestoreDone();
			SMSSendTask smsSendTask = new SMSSendTask(_context, _account);
			smsSendTask.execute();
		}
	}

	class SMSSendTask extends AsyncTask<Void, Integer, Void> {
		private final Context _context;
		private final Account _account;

		public SMSSendTask(Context context, Account account) {
			_context = context;
			_account = account;
		}

		@Override
		protected Void doInBackground(Void... params) {
			SmsSendStackProvider stackProvider = SmsSendStackProvider.getInstance(_context);
			List<SmsMessage> smsToSend = stackProvider.getMessagestoSend();

			for (SmsMessage message : smsToSend) {
				if(message.getSent() == 0){
					Intent intent = new Intent();
					intent.putExtra("sms", SmsSendStackProvider.smsMessageToJsonString(message));
					intent.setClass(_context, SmsSenderIntentReceiver.class);
					SmsManager smsManager = SmsManager.getDefault();
					//smsManager.sendTextMessage(message.getAddress(), null, message.getMessage(),
					//		PendingIntent.getBroadcast(_context, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT), null);
					stackProvider.setSent(message, 2);
				}

			}


			return null;
		}



		@Override
		protected void onPostExecute(Void aVoid) {
			super.onPostExecute(aVoid);
		}
	}

	String TAG = ASyncSMSSender.class.getSimpleName();
}
