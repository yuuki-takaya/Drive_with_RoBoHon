package com.example.tomoki.myheartrate;

import android.os.AsyncTask;
import android.util.Log;

import java.net.*;
import java.io.*;

public class MySocket extends Socket {

    // ポート番号
	public static final int DEFAULT_PORT = 10010;
    /**
     * デフォルトで使用されるホスト名
     */

    // データの送信先（データベース）
	public static final String DEFAULT_HOST = "172.20.11.195";

    MySocket s;
    private OutputStream os;
    private BufferedWriter bw;

    /**
     * デフォルトのホスト名とポート番号を使用して接続します．
     */
    public MySocket() throws UnknownHostException, IOException {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    /**
     * ホスト名とポート番号を指定して接続します．
     * @param host ホスト名
     * @param port ポート番号
     */
    public MySocket(final String host, final int port) throws UnknownHostException, IOException {
		super(host, port);
		try {
			os = getOutputStream();
			bw = new BufferedWriter(new OutputStreamWriter(os));
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
    }

    public void send(String text) throws IOException {
        if (bw!=null){
            bw.write(text+"\n");
            Log.d("Socket","can");
            bw.flush();
        }
	}

    public void closeSocket() throws IOException {
        if (s.isConnected()){
            s.close();
        }
    }
}
