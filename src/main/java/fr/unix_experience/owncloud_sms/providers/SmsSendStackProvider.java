package fr.unix_experience.owncloud_sms.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.telephony.SmsManager;
import android.util.JsonReader;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ncsmsgo.SmsMessage;

public class SmsSendStackProvider {
    private static final String DATABASE_NAME = "sms_db";
    private static final int DATABASE_VERSION = 3


            ;
    private static final String TABLE_NAME = "send_stack";
    private static final String	KEY_MESSAGE = "message";
    /*
        0: not sent,
        1: sent,
        2: pending

     */
    private static final String	KEY_SENT = "sent";
    private static final String	KEY_ADDRESS = "address";
    private static final String	KEY_MAILBOX_ID = "mb_is";
    private static final String	KEY_DATE = "date";
    private static final String	KEY_BODY = "body";
    public static final String CREATE_TABLE = "create table " + TABLE_NAME + "( "
            + KEY_SENT + " INTEGER,"
            + KEY_ADDRESS + " text not null,"
            + KEY_MAILBOX_ID + " INTEGER not null,"
            + KEY_DATE + " text not null,"
            + KEY_BODY + " INTEGER not null,"
            + KEY_MESSAGE + " text not null,"
            +" PRIMARY KEY ("+KEY_ADDRESS+","+KEY_BODY+","+KEY_DATE+","+KEY_MAILBOX_ID+" ));";
    private static SmsSendStackProvider sSmsSendStackProvider;
    private final Context mContext;
    private final DatabaseHelper mDatabaseHelper;

    public SmsSendStackProvider(Context context){
        mContext = context;
        mDatabaseHelper = new DatabaseHelper(context);
    }

    public void addMessage(SmsMessage message, int conflit) {
        synchronized (mDatabaseHelper.lock) {
            SQLiteDatabase sqliteDatabase = mDatabaseHelper.open();
            ContentValues initialValues = new ContentValues();
            initialValues.put(KEY_MESSAGE,smsMessageToJsonString(message));
            initialValues.put(KEY_SENT,message.getSent());
            initialValues.put(KEY_ADDRESS,message.getAddress());
            initialValues.put(KEY_MAILBOX_ID,message.getMailbox());
            initialValues.put(KEY_DATE,message.getDate());
            initialValues.put(KEY_BODY,message.getMessage());
            sqliteDatabase.insertWithOnConflict(TABLE_NAME, null, initialValues, conflit);
            mDatabaseHelper.close();
        }

    }

    public void addMessage(SmsMessage message) {
        try {
            addMessage(message, SQLiteDatabase.CONFLICT_NONE);
        } catch (android.database.sqlite.SQLiteConstraintException e){

        }
    }
    public static SmsSendStackProvider getInstance(Context context){
        if(sSmsSendStackProvider ==null)
            sSmsSendStackProvider = new SmsSendStackProvider(context);
        return sSmsSendStackProvider;
    }

    public boolean messageExists() {
        List<SmsMessage> messages = new ArrayList<>();
        synchronized (mDatabaseHelper.lock) {
            SQLiteDatabase sqliteDatabase = mDatabaseHelper.open();
            Cursor cursor = sqliteDatabase.query(TABLE_NAME, new String[]{KEY_MESSAGE, KEY_SENT}, KEY_SENT + "= ?", new String[]{"0"}, null, null, null);
            if (cursor.getCount() > 0) {
                mDatabaseHelper.close();
                cursor.close();
                return true;
            }
            mDatabaseHelper.close();
            cursor.close();
        }
        return false;
    }

    public static SmsMessage smsMessageFromString(String msg) throws JSONException {
        SmsMessage message = new SmsMessage();
        JSONObject object = new JSONObject(msg);
        message.setAddress(object.getString("Address"));
        message.setMailbox(object.getInt("Mailbox"));
        message.setMessage(object.getString("Message"));
        message.setSent(object.getInt("Sent"));
        message.setCardNumber(object.getString("CardNumber"));
        message.setIccId(object.getString("IccId"));
        message.setDeviceName(object.getString("DeviceName"));
        message.setCarrierName(object.getString("CarrierName"));
        message.setType(object.getInt("Type"));
        message.setDate(object.getLong("Date"));

        return message;
    }
    public List<SmsMessage> getMessagestoSend() {
        List<SmsMessage> messages = new ArrayList<>();
        synchronized (mDatabaseHelper.lock) {
            SQLiteDatabase sqliteDatabase = mDatabaseHelper.open();
            Cursor cursor = sqliteDatabase.query(TABLE_NAME, new String[]{KEY_MESSAGE, KEY_SENT}, KEY_SENT + "= ?", new String[]{"0"}, null, null, null);
            if (cursor.getCount() > 0) {
                while(cursor.moveToNext()){
                    try {
                        messages.add(smsMessageFromString(cursor.getString(cursor.getColumnIndex(KEY_MESSAGE))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            cursor.close();
            mDatabaseHelper.close();
        }

        return messages;
    }
    //Quoting (SmsMessage.toString wasn't quoting)
    public static String smsMessageToJsonString(SmsMessage message) {
        StringBuilder var1 = new StringBuilder();
        var1.append("{");
        var1.append("Address:").append(JSONObject.quote(message.getAddress())).append(",");
        var1.append("Mailbox:").append(message.getMailbox()).append(",");
        var1.append("Message:").append(JSONObject.quote(message.getMessage())).append(",");
        var1.append("Sent:").append(message.getSent()).append(",");
        var1.append("CardNumber:").append(JSONObject.quote(message.getCardNumber())).append(",");
        var1.append("IccId:").append(JSONObject.quote(message.getIccId())).append(",");
        var1.append("DeviceName:").append(JSONObject.quote(message.getDeviceName())).append(",");
        var1.append("CarrierName:").append(JSONObject.quote(message.getCarrierName())).append(",");
        var1.append("Type:").append(message.getType()).append(",");
        var1.append("Date:").append(message.getDate());
        return var1.append("}").toString();
    }

    public void setSent(SmsMessage message, int sent) {
        message.setSent(sent);
        updateMessage(message);
    }

    private void updateMessage(SmsMessage message) {
        synchronized (mDatabaseHelper.lock) {
            SQLiteDatabase sqliteDatabase = mDatabaseHelper.open();
            ContentValues initialValues = new ContentValues();
            initialValues.put(KEY_MESSAGE,smsMessageToJsonString(message));
            initialValues.put(KEY_SENT,message.getSent());

            sqliteDatabase.update(TABLE_NAME, initialValues, KEY_ADDRESS+"=? AND "+KEY_MAILBOX_ID+"= ? AND "+KEY_DATE+"= ? AND "+KEY_BODY +" = ?",
                    new String[]{message.getAddress(), message.getMailbox()+"", message.getDate()+"", message.getMessage()});
            mDatabaseHelper.close();
        }
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        public Object lock = new Object();
        private DatabaseHelper mDatabaseHelper;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL(CREATE_TABLE);

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("drop table "+TABLE_NAME);
            db.execSQL(CREATE_TABLE);


        }

        public SQLiteDatabase open(){
            if(mDatabaseHelper == null)
                mDatabaseHelper = new DatabaseHelper(mContext);
            return mDatabaseHelper.getWritableDatabase();
        }

        public void close(){
            if(mDatabaseHelper == null)
                mDatabaseHelper.close();
        }
    }
}
