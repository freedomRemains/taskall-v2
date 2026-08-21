package com.freedom.taskall_v2.common.service.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * jakarta.mail(Angus Mail)のIMAPStore#connect()が実際に発行するワイヤプロトコル
 * (CAPABILITY/LOGIN/LOGOUT)に最小限応答する、テスト専用の簡易IMAPサーバです。
 *
 * <p>
 * {@link JavaMailMailboxAccessVerifier}はMockitoでモック化できない実際のIMAP接続を行うため、
 * 単体テストだけでは「本物のIMAPサーバに対してLOGINが成功/失敗する」ケースを検証できていなかった
 * (issue #96のユーザ報告により判明)。本クラスを使うことで、Dockerなしで実際のIMAPプロトコル
 * ハンドシェイクを通した検証が行える。
 * </p>
 */
class FakeImapServer implements AutoCloseable {

    private static final Pattern LOGIN_PATTERN = Pattern
            .compile("(\\S+)\\s+LOGIN\\s+\"?([^\"\\s]+)\"?\\s+\"?([^\"\\s]+)\"?", Pattern.CASE_INSENSITIVE);

    private final String expectedUser;
    private final String expectedPassword;
    private final ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = true;

    FakeImapServer(String expectedUser, String expectedPassword) throws IOException {
        this.expectedUser = expectedUser;
        this.expectedPassword = expectedPassword;
        this.serverSocket = new ServerSocket(0);
        executor.submit(this::acceptLoop);
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleClient(socket));
            } catch (IOException e) {
                // サーバソケットをclose()した際にaccept()が例外を投げるのは正常終了なので無視する
                break;
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
                OutputStream out = socket.getOutputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.US_ASCII))) {

            write(out, "* OK IMAP4rev1 fake server ready\r\n");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[FakeImapServer] received: " + line);
                String tag = line.split("\\s+", 2)[0];

                if (line.toUpperCase(java.util.Locale.ROOT).contains("CAPABILITY")) {
                    // 実際のGreenMailのCAPABILITY応答は「AUTH=XOAUTH2」のみでLOGIN/PLAINの
                    // SASL機構は広告しないため、それに合わせてAUTH=LOGIN等は含めない
                    // (advertiseした場合、Angus MailがAUTHENTICATE LOGINへ切り替わってしまう)
                    write(out, "* CAPABILITY IMAP4rev1 SASL-IR AUTH=XOAUTH2\r\n");
                    write(out, tag + " OK CAPABILITY completed\r\n");
                } else if (line.toUpperCase(java.util.Locale.ROOT).contains("LOGIN")) {
                    Matcher matcher = LOGIN_PATTERN.matcher(line);
                    boolean authenticated = matcher.find() && expectedUser.equals(matcher.group(2))
                            && expectedPassword.equals(matcher.group(3));
                    write(out, authenticated ? tag + " OK LOGIN completed\r\n" : tag + " NO LOGIN failed\r\n");
                } else if (line.toUpperCase(java.util.Locale.ROOT).contains("LOGOUT")) {
                    write(out, "* BYE logging out\r\n");
                    write(out, tag + " OK LOGOUT completed\r\n");
                    return;
                } else {
                    write(out, tag + " OK done\r\n");
                }
            }
        } catch (IOException e) {
            // クライアント切断等は無視する(テスト用の使い捨てサーバであるため)
        }
    }

    private void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            // クローズ失敗はテスト後始末なので無視する
        }
        executor.shutdownNow();
    }
}
