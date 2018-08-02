package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

/*
Citation: Used code from PA2B
MatrixCursor: https://developer.android.com/reference/android/database/MatrixCursor.html
AsyncTask .get() function: https://developer.android.com/reference/android/os/AsyncTask.html#get()
 */

public class SimpleDhtProvider extends ContentProvider {

    private static final String TAG = "SimpleDhtProvider";
    private SimpleDhtDBHelper gDBHelper = null;
    private static final String DATABASE_NAME = "simpledht";
    private static final int DATABASE_VERSION = 1;
    private SQLiteDatabase gDB;
    private Uri mUri;

    static final int SERVER_PORT = 10000;

    private int myPort; //stores the port number
    private String serialNum; //Serial Number of current node
    private String nodeID; //stores the hashed value of the serial number
    private int predecessor; //stores the predecessor of the node
    private int successor; //stores the successor of the node

    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreate(){
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");

        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        Log.d(TAG,tel.getLine1Number());
        serialNum = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        Log.d(TAG,serialNum);
        myPort = Integer.parseInt(serialNum) * 2;

        //Generate node ID
        nodeID = genHash(serialNum);
        Log.v(TAG, "Node ID: "+nodeID);

        //Initialize database
        gDBHelper = new SimpleDhtDBHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION);

        //Initialize serversocket
        try{
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }
        catch(IOException ioe){
            Log.e(TAG, "Can't create a ServerSocket");
            ioe.printStackTrace();
        }

        predecessor = myPort;
        successor = myPort;

        if(serialNum.equals("5554")){
            predecessor = myPort;
            successor = myPort;
            Log.v("Predecessor Successor: ",predecessor+" : "+successor);
        }
        else{
            Message nodeJoinMsg = new NodeMessage("J",null, myPort, 11108);
            Log.d(TAG,"Node Join Message: "+nodeJoinMsg.toString());
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, nodeJoinMsg.toString());
        }
        return true;
    }

    /*
    A helper function which compares the key with the current node ID to see whether the key belongs to this node
    It returns true if yes, else returns false
     */
    private boolean ifCurrentNode(String key){
        String hash_key = genHash(key);
        String hash_predecessor = genHash(String.valueOf(predecessor/2));
        //if only one node in the chord ring, insert the values in it or insert the node after it
        if(predecessor == successor && predecessor == myPort){
            return true;
        }
        //if key/node greater than predecessor and predecessor greater than current node
        else if(hash_key.compareTo(hash_predecessor)>0 && hash_predecessor.compareTo(nodeID) > 0){
            return true;
        }
        //if key/node lesser than current node and predecessor greater than current node
        else if(hash_key.compareTo(nodeID)<0 && hash_predecessor.compareTo(nodeID) > 0){
            return true;
        }
        //if key/node lesser than or equal to current node and predecessor lesser than key, then insert in current node
        else if(hash_key.compareTo(nodeID) <= 0 && hash_key.compareTo(hash_predecessor) > 0){
            return true;
        }
        return false;
    }

    //Citation: Android developer documentation
    //https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#delete(java.lang.String, java.lang.String, java.lang.String[])
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int deletedRows = 0;

        //key appended with origin serial number  (i.e. where the request was made)
        String[] para = selection.split(";");

        try {
            Cursor result = null;
            Log.v("delete", selection);

            //if local delete, delete all the keys stored within current node
            if (para[0].equals("@")) {
                gDB = gDBHelper.getWritableDatabase();
                deletedRows = gDB.delete("content", null, null);
            }
            //if global delete, delete all the keys stored within the Chord ring
            else if (para[0].equals("*")) {
                Log.v("Star", "Enter the star condition!");

                //If only one node
                if (predecessor == successor && predecessor == myPort) {
                    gDB = gDBHelper.getWritableDatabase();
                    deletedRows = gDB.delete("content", null, null);
                    return deletedRows;
                }
                if (para.length > 1) {
                    if (para[1].equals(serialNum)) {
                        Log.v("Star", "If the origin = serialNum. One loop completed");
                        return deletedRows;
                    }
                }
                Message deleteMsg = null;
                if (selection.contains(";")) {
                    Log.v("Star", "It is a forwarded message from " + para[1] + " : " + selection);
                    deleteMsg = new DataMessage("D", para[1], successor, selection, null);
                } else {
                    Log.v("Star", "If the message has just arrived " + selection);
                    deleteMsg = new DataMessage("D", serialNum, successor, para[0] + ";" + serialNum, null);
                    Log.v("Star", "Message sent: " + deleteMsg);
                }
                //Delete your own data
                gDB = gDBHelper.getWritableDatabase();
                deletedRows = gDB.delete("content", null, null);

                //invoke asynctask to send the delete message to successor
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg.toString());
            }
            //if only one key requested
            else {
                //check if the key belongs to current node, then delete it
                Log.v(TAG,"To de deleted: "+para[0]);
                if (ifCurrentNode(para[0])) {
                    Log.v("delete", "In current Node: "+selection.toString());

                    gDB = gDBHelper.getWritableDatabase();
                    try {
                        String[] keys = {para[0]};
                        String selClause = "key = ?";
                        Log.d("Delete", selection);
                        deletedRows = gDB.delete("content", selClause, keys);
                    } catch (SQLiteException sqle) {
                        Log.e(TAG, "delete failed : " + sqle.toString());
                    }
                    Log.v("delete done: ", selection.toString());
                }
                //else forward the delete message to successor
                else {
                    Log.v("delete", "In current Node: "+selection.toString());
                    Message deleteMsg = null;
                    if (selection.contains(";")) {
                        deleteMsg = new DataMessage("D", para[1], successor, selection, null);
                        Log.d(TAG, "Delete Message: " + deleteMsg.toString());
                        if(successor != Integer.parseInt(para[1])*2)
                            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg.toString());
                    } else {
                        deleteMsg = new DataMessage("D", serialNum, successor, para[0] + ";" + serialNum, null);
                        Log.d(TAG, "Delete Message: " + deleteMsg.toString());
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteMsg.toString());
                    }
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return deletedRows;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String key = (String) values.get(KEY_FIELD);
        String value = (String) values.get(VALUE_FIELD);


        Log.d(TAG,"Predecessor and successor : "+predecessor + " " +successor);
        //if any of the current node conditions satisfy
        if(ifCurrentNode(key)){
            Log.d(TAG,"Insert key: "+key);
            gDB = gDBHelper.getWritableDatabase();
            try {
                gDB.insertWithOnConflict("content", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            catch(SQLiteException sqle){
                Log.e(TAG, "insertWithOnConflict failed : "+sqle.toString());
            }
            Log.v("insert", values.toString());
        }
        //else forward the insert message to successor
        else{
            Message insertMsg = new DataMessage("I",serialNum, successor, key, value);
            Log.d(TAG,"Insert Message: "+insertMsg.toString());
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, insertMsg.toString());
        }

        return uri;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String[] columnNames = {KEY_FIELD,VALUE_FIELD};
        MatrixCursor matrixCursor = new MatrixCursor(columnNames);

        String[] para = selection.split(";");

        try{
            Cursor result = null;
            Log.v("query", selection);

            //if local dump, return all the keys stored with current node
            if(para[0].equals("@")){
                gDB = gDBHelper.getReadableDatabase();
                result = gDB.query("content",null,null,null,null,null,null);
                result.moveToFirst();
            }
            //if global dump, return all the keys stored within the Chord ring
            else if(para[0].equals("*")){
                Log.v("Star","Enter the star condition!");

                //If you are the only node, just query your database and send the result
                if(predecessor == successor && predecessor == myPort) {
                    Log.v("Star","If only one node!");
                    gDB = gDBHelper.getReadableDatabase();
                    result = gDB.query("content",null,null,null,null,null,null);
                    result.moveToFirst();
                    return result;
                }
                if(para.length > 1){
                    if(para[1].equals(serialNum)) {
                        Log.v("Star","If the origin = serialNum. One loop completed");
                        return null;
                    }
                }
                Message queryMsg = null;
                if(selection.contains(";")) {
                    Log.v("Star","It is a forwarded message from "+ para[1] + " : " +selection);
                    queryMsg = new DataMessage("Q", para[1], successor, selection, null);

                    //Add you own data in MatrixCursor
                    gDB = gDBHelper.getReadableDatabase();
                    result = gDB.query("content",null,null,null,null,null,null);
                    if(result != null && result.getCount() >0){
                        result.moveToFirst();
                        do{
                            Object[] objValues = new Object[]{result.getString(0),result.getString(1)};
                            matrixCursor.addRow(objValues);
                            Log.v("Star","Added my own data: key:"+result.getString(0)+" value-"+result.getString(1));
                        }while(result.moveToNext());
                    }
                }
                else {
                    Log.v("Star","If the message has just arrived "+selection);
                    queryMsg = new DataMessage("Q", serialNum, successor, para[0] + ";" + serialNum, null);
                    Log.v("Star","Message sent: "+queryMsg);
                    //Add your own data in MatrixCursor
                    gDB = gDBHelper.getReadableDatabase();
                    result = gDB.query("content",null,null,null,null,null,null);
                    if(result != null && result.getCount() >0){
                        result.moveToFirst();
                        do{
                            Object[] objValues = new Object[]{result.getString(0),result.getString(1)};
                            matrixCursor.addRow(objValues);
                            Log.v("Star","Added my own data: key:"+result.getString(0)+" value-"+result.getString(1));
                        }while(result.moveToNext());
                    }
                }
                Log.v("Star","Invoking client task");
                //invoke asynctask to send the query to successor and wait on this task to get the result back
                String resultString = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsg.toString()).get();

                //append the result obtained in matrixcursor
                if(resultString != null){
                    String[] par = resultString.split("#");
                    Log.d("Star", "Key-value pairs: " + par[3] + ": "+par[4]);
                    if(!par[3].equals("QEND")){
                        String[] keys = par[3].split(":");
                        String[] values = par[4].split(":");
                        for(int i = 0; i<keys.length;i++){
                            Object[] objValues = new Object[]{keys[i],values[i]};
                            matrixCursor.addRow(objValues);
                            Log.v("Star","Added received data in cursor: key:"+keys[i]+" value-"+values[i]);
                        }
                    }
                }
                return matrixCursor;
            }
            //if only one key requested
            else{
                //check if the key belongs to current node
                if(ifCurrentNode(para[0])){
                    result = null;
                    String[] keys = {para[0]};
                    String selClause = "key = ?";
                    //Log.d(TAG, selClause);
                    result = gDB.query("content", projection, selClause, keys, null, null, sortOrder);

                    result.moveToFirst();
                }
                //else if the key doesn't belong to you, forward the query message to successor
                else{
                    Message queryMsg = null;
                    //if it is a forwarded query message, forward it as it is else append your serialNum to the key and send it further
                    if(selection.contains(";")) {
                        queryMsg = new DataMessage("Q",para[1], successor, selection, null);
                    }
                    else
                        queryMsg = new DataMessage("Q",serialNum, successor, para[0]+";"+serialNum, null);
                    Log.d(TAG,"Query Message: "+queryMsg.toString());
                    String resultString = new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryMsg.toString()).get();
                    String[] par = resultString.split("#");

                    //If it is a QEND message, return null
                    if(par[3].equals("QEND"))
                        return null;
                    //Add the key value in a MatrixCursor and return it
                    Log.d(TAG, "Key-value pairs: " + par[3] + ": "+par[4]);
                    Object[] objValues = new Object[]{par[3],par[4]};
                    matrixCursor.addRow(objValues);
                    return matrixCursor;
                }
            }

            return result;
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }



    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input){
        Formatter formatter = new Formatter();
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] sha1Hash = sha1.digest(input.getBytes());

            for (byte b : sha1Hash) {
                formatter.format("%02x", b);
            }
        }
        catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return formatter.toString();
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Log.d(TAG,"Server socket created!");

            while(true) {
//                Log.d(TAG,"Inside server while");
                try {
//                    Log.d(TAG,"Inside server try");
                    Socket socket = serverSocket.accept();
//                    Log.d(TAG,"Socket is connected: "+socket.isConnected()+" "+socket.getRemoteSocketAddress());
                    InputStreamReader clientInput = new InputStreamReader(socket.getInputStream());
                    BufferedReader in = new BufferedReader(clientInput);
                    String str = in.readLine();
                    Log.d(TAG,"Message read on server side: "+str);

                    if(str != null){
                        String[] msgParameters = str.split("#");

                        //If a node join message
                        if(str.startsWith("J")){
                            String newSerialNum = String.valueOf(Integer.parseInt(msgParameters[3])/2);
                            StringBuilder data = new StringBuilder();
                            //If the current node should have the new node as predecessor
                            if(ifCurrentNode(newSerialNum)){
                                //Data distribution if new node joins
                                //Get the data which is lesser than or equal to newSerialNum
                                gDB = gDBHelper.getWritableDatabase();
                                Cursor result = gDB.query("content",null,null,null,null,null,null);
                                if(result != null && result.getCount() >0){
                                    result.moveToFirst();
                                    do{
                                        String key = result.getString(0);
                                        String value = result.getString(1);
                                        if(genHash(key).compareTo(genHash(newSerialNum))<=0){
                                            data.append(key+"-"+value);
                                            String[] keys = {key};
                                            gDB.delete("content","key = ?", keys);
                                        }
                                        data.append(";");
                                        data.deleteCharAt(data.length()-1);
                                    }while(result.moveToNext());
                                }
                                if(data.length() == 0)
                                    publishProgress("UD#"+msgParameters[3]);
                                else //if there exists some data
                                    publishProgress("UD#"+msgParameters[3]+"#"+data.toString());
                            }
                            //newNodeID is greater than the current node ID
                            else{
                                Log.d(TAG, "newNodeID is greater than the current node ID and lesser than the predecessor ");
                                Message joinForwardMsg = new NodeMessage("J", "5554", Integer.parseInt(msgParameters[3]), successor);
                                Log.d(TAG, "forwarded message: "+joinForwardMsg.toString());
                                publishProgress(joinForwardMsg.toString());
                            }
                        }
                        else if(str.startsWith("UD")){ //If the message is Update Node links
                            Log.v(TAG, "Before update: Predecessor: "+predecessor+" Successor: "+successor);
                            if(!msgParameters[4].equals("0"))
                                predecessor = Integer.parseInt(msgParameters[4]);
                            if(!msgParameters[5].equals("0"))
                                successor = Integer.parseInt(msgParameters[5]);

                            Log.v(TAG, "After update: Predecessor: "+predecessor+" Successor: "+successor);
                        }
                        else if(str.startsWith("I")){ //If the message is insert
                            ContentValues cv = new ContentValues();
                            cv.put(KEY_FIELD, msgParameters[3]);
                            cv.put(VALUE_FIELD, msgParameters[4]);
                            insert(mUri, cv);
                        }
                        else if(str.startsWith("Q")){ //If the message is queue request
                            //if you have completed one loop and reached the origin node, write "QEND"
                            Log.v("Star","Inside server, msg: "+str);
                            if(serialNum.equals(msgParameters[1])){
                                Message queryReplyMsg = new DataMessage("Q", msgParameters[1], Integer.parseInt(msgParameters[1])*2, "QEND",null);
                                Log.v("Star","Server replies: Completed one loop - "+queryReplyMsg.toString());
                                PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                                printWriter.println(queryReplyMsg.toString());
                                printWriter.flush();
                            }
                            else {
                                Cursor resultCursor = query(mUri, null, msgParameters[3], null, null);
                                Log.v("Star","Server resultCursor count - "+resultCursor.getCount());
                                String result="";
                                //If data exists, write it on the same socket
                                if (resultCursor != null && resultCursor.moveToFirst()) {
                                    Message queryReplyMsg = new DataMessage("Q", msgParameters[1], Integer.parseInt(msgParameters[1]) * 2, resultCursor);
                                    Log.v("Star","Server replies: not end of loop - "+queryReplyMsg.toString());
                                    result = queryReplyMsg.toString();
                                }
                                else{ //Just write "QEND"
                                    Message queryReplyMsg = new DataMessage("Q", msgParameters[1], Integer.parseInt(msgParameters[1]) * 2, "QEND", null);
                                    Log.v("Star","Server replies: not end of loop - "+queryReplyMsg.toString());
                                    result = queryReplyMsg.toString();
                                }
                                    PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                                    printWriter.println(result);
                                    printWriter.flush();
                            }
                        }
                        else if(str.startsWith("D")){ //If the message is delete request
                            Log.v("Star","Inside server, msg: "+str);
                            delete(mUri, msgParameters[3], null);
                        }
                    }

                } catch (IOException ioe) {
                    Log.e(TAG, "ServerTask socket IOException");
                    ioe.printStackTrace();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            //Invoke the client AsyncTask in publishProgress as cannot be invoked from doInBackground
            String msg = strings[0];
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... msgs) {
            String msg = msgs[0];
            String[] parameters = msg.split("#");
            String result = null;

            if(msg.startsWith("J")){ //Node Join message
                //send node join message onto 5554 or any successor node in the ring until the right node is found
                Log.v(TAG,"Write on port: "+Integer.parseInt(parameters[2]));
                writeOnSocket(Integer.parseInt(parameters[2]), msg);
            }
            else if(msg.startsWith("UD")){ //once the node is found, Update links message
                int newNodePortNum = Integer.parseInt(msg.substring(msg.indexOf("#")+1,msg.length()));
                Log.v(TAG, "New node port num: "+newNodePortNum);

                //If more than one node in the ring
                if(myPort != predecessor) {
                    //Send message to update the successor of the current node's predecessor to the new node
                    Message predMsg = new NodeMessage("UD", 0, newNodePortNum, predecessor);
                    writeOnSocket(predecessor, predMsg.toString());

                    //Send message to new node to update its successor->current node and predecessor->current node's predecessor
                    Message newNodeMsg = new NodeMessage("UD", predecessor, myPort, newNodePortNum);
                    writeOnSocket(newNodePortNum, newNodeMsg.toString());

                    //If there exists some data to be sent to the new node
                    if(parameters.length >2){
                        String[] pairs = parameters[2].split(";");
                        for(String p: pairs){
                            String[] kv = p.split("-");
                            String key = kv[0];
                            String value = kv[1];
                            Message newDataMsg = new DataMessage("I",serialNum,newNodePortNum,key,value);
                            writeOnSocket(newNodePortNum, newDataMsg.toString()); //Write the data one-by-one
                        }
                    }

                    predecessor = newNodePortNum;
                }
                //If only one node, so send message only to new Node
                else{
                    //Send message to new node to update its successor->current node and predecessor->current node's predecessor
                    Message newNodeMsg = new NodeMessage("UD", predecessor, myPort, newNodePortNum);
                    writeOnSocket(newNodePortNum, newNodeMsg.toString());

                    //If there exists some data to be sent to the new node
                    if(parameters.length >2){
                        String[] pairs = parameters[2].split(";");
                        for(String p: pairs){
                            String[] kv = p.split("-");
                            String key = kv[0];
                            String value = kv[1];
                            Message newDataMsg = new DataMessage("I",serialNum,newNodePortNum,key,value);
                            writeOnSocket(newNodePortNum, newDataMsg.toString());
                        }
                    }

                    predecessor = newNodePortNum;
                    successor = newNodePortNum;
                }
            }
            else if(msg.startsWith("I")){ //Insert message
                Log.v(TAG,"Write on port: "+Integer.parseInt(parameters[2]));
                writeOnSocket(Integer.parseInt(parameters[2]), msg);
            }
            else if(msg.startsWith("Q")){ //Query message
                Log.v("Star","Client: Write on port: "+Integer.parseInt(parameters[2]));
                result = writeOnSocket(Integer.parseInt(parameters[2]), msg);
            }
            else if(msg.startsWith("D")){ //Delete message
                Log.v("Star","Client: Write on port: "+Integer.parseInt(parameters[2]));
                result = writeOnSocket(Integer.parseInt(parameters[2]), msg);
            }
            Log.v(TAG, "Predecessor: "+predecessor+" Successor: "+successor);
            return result;
        }

        //A helper function to connect to server and write the message
        public String writeOnSocket(int remotePort, String msg){
            Log.v(TAG, "Message to write: "+msg+" on "+remotePort);

            String[] msgParameters = msg.split("#");
            try {
                String result;
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort));

                PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                printWriter.println(msg);
                printWriter.flush();
                Log.v("Star","Client: Write message: "+msg);

                if(msgParameters[0].equals("Q")){
                    InputStreamReader input = new InputStreamReader(socket.getInputStream());
                    BufferedReader in = new BufferedReader(input);
                    result = in.readLine();
                    Log.v("Star","Client: Result: "+result);
                    if(result != null)
                        return result;
                }
            }
            catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException on Message read: "+e.getMessage());
                e.printStackTrace();

                //trying to connect to 5554 but it does not exist
                if(msgParameters[0].equals("J") && msgParameters[2].equals("11108")) {
                    Log.e(TAG, "5554 not present");
                    predecessor = myPort;
                    successor = myPort;
                    Log.v(TAG, "Predecessor: "+predecessor+" Successor: "+successor);
                }
            }
            return null;
        }
    }
}
