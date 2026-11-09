package server;

import java.sql.Timestamp;

/** Một tin nhắn lưu trong CSDL. */
public record Msg(long id, String sender, String kind, String target, String media,
                  String content, String fileName, String filePath, Timestamp time) {
}
