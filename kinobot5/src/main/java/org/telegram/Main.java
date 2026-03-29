package org.telegram;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.ApproveChatJoinRequest;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Main extends TelegramLongPollingBot {

    // ╔══════════════════════════════════════════════════╗
    // ║  1) ADMIN ID  ← @userinfobot ga yozib toping    ║
    // ╚══════════════════════════════════════════════════╝
    private static final long   SUPER_ADMIN_ID       = 7799807980L;
    private static final String SUPER_ADMIN_USERNAME = "noyabirl1k";

    // ╔══════════════════════════════════════════════════╗
    // ║  2) BOT TOKEN                                    ║
    // ╚══════════════════════════════════════════════════╝
    private static final String BOT_TOKEN = "8456287018:AAHWtv88IGgH-UL9K3NZ3UZRIJriAm9aHu4";

    // ╔══════════════════════════════════════════════════╗
    // ║  3) BOT USERNAME  ← @ belgisisiz                ║
    // ╚══════════════════════════════════════════════════╝
    private static final String BOT_USERNAME = "film1fy_bot";

    // ╔══════════════════════════════════════════════════╗
    // ║  4) MAJBURIY KANALLAR (default, DB dan yuklanadi)║
    // ╚══════════════════════════════════════════════════╝
    private final List<Channel> channels = new CopyOnWriteArrayList<>(List.of(
        new Channel("KANAL 1", -1003705247131L, "https://t.me/+qEQdd6Y-zE4zYTYy"),
        new Channel("KANAL 2", -1003837469684L, "https://t.me/+jCnxEHBOLogyMDMy"),
        new Channel("KANAL 3", -1003896851782L, "https://t.me/+3rcCxojAHT8xYjI6")
    ));

    // ═══════════════════════════════════════════════════
    //  RECORDLAR
    // ═══════════════════════════════════════════════════
    record Movie(String code, String title, List<String> fileIds) {}
    record Channel(String name, long id, String link) {}

    // ═══════════════════════════════════════════════════
    //  DATABASE
    // ═══════════════════════════════════════════════════
    private Connection db;

    private void initDb() {
        try {
            // DB faylini loyiha papkasiga emas, foydalanuvchi home'iga saqlaymiz
            String dbPath = System.getProperty("user.home") + "/kinobot.db";
            db = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            db.createStatement().execute("PRAGMA journal_mode=WAL");
            db.createStatement().execute("PRAGMA busy_timeout=15000");
            db.createStatement().execute("PRAGMA synchronous=NORMAL");
            db.setAutoCommit(true);

            // Jadvallar
            db.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS movies (
                    code    TEXT NOT NULL,
                    file_id TEXT NOT NULL,
                    title   TEXT NOT NULL,
                    part    INTEGER NOT NULL DEFAULT 1,
                    added   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");

            db.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id         INTEGER PRIMARY KEY,
                    username   TEXT,
                    first_name TEXT,
                    last_seen  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");

            db.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS admins (
                    id       INTEGER PRIMARY KEY,
                    username TEXT,
                    added_by INTEGER,
                    added    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""");

            db.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS channels (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    cid  INTEGER NOT NULL UNIQUE,
                    link TEXT NOT NULL
                )""");

            // Indeks
            db.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_movies_code ON movies(code)");

            // Kanallarni DB dan yuklaymiz (agar bo'lsa)
            loadChannelsFromDb();

            System.out.println("✅ DB tayyor: " + dbPath);
        } catch (SQLException e) {
            System.err.println("❌ DB xato: " + e.getMessage());
        }
    }

    // ─── KANALLAR ───────────────────────────────────────
    private void loadChannelsFromDb() {
        try {
            ResultSet rs = db.createStatement().executeQuery(
                "SELECT name,cid,link FROM channels ORDER BY id");
            List<Channel> list = new ArrayList<>();
            while (rs.next())
                list.add(new Channel(rs.getString("name"), rs.getLong("cid"), rs.getString("link")));
            rs.close();
            if (!list.isEmpty()) {
                channels.clear();
                channels.addAll(list);
            } else {
                // Default kanallarni DB ga yozamiz
                for (Channel ch : channels) dbAddChannel(ch.name(), ch.id(), ch.link());
            }
        } catch (SQLException e) {
            System.err.println("loadChannels xato: " + e.getMessage());
        }
    }

    private boolean dbAddChannel(String name, long cid, String link) {
        try {
            PreparedStatement ps = db.prepareStatement(
                "INSERT OR REPLACE INTO channels (name,cid,link) VALUES (?,?,?)");
            ps.setString(1, name); ps.setLong(2, cid); ps.setString(3, link);
            ps.executeUpdate();
            channels.removeIf(c -> c.id() == cid);
            channels.add(new Channel(name, cid, link));
            return true;
        } catch (SQLException e) { return false; }
    }

    private boolean dbDelChannel(long cid) {
        try {
            PreparedStatement ps = db.prepareStatement("DELETE FROM channels WHERE cid=?");
            ps.setLong(1, cid);
            int r = ps.executeUpdate();
            channels.removeIf(c -> c.id() == cid);
            return r > 0;
        } catch (SQLException e) { return false; }
    }

    // ─── ADMINLAR ───────────────────────────────────────
    private boolean isAdminInDb(long uid) {
        try {
            PreparedStatement ps = db.prepareStatement("SELECT 1 FROM admins WHERE id=?");
            ps.setLong(1, uid);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    private boolean dbAddAdmin(long id, String uname, long addedBy) {
        try {
            PreparedStatement ps = db.prepareStatement(
                "INSERT OR REPLACE INTO admins (id,username,added_by) VALUES (?,?,?)");
            ps.setLong(1, id); ps.setString(2, uname); ps.setLong(3, addedBy);
            ps.executeUpdate(); return true;
        } catch (SQLException e) { return false; }
    }

    private boolean dbDelAdmin(long id) {
        try {
            PreparedStatement ps = db.prepareStatement("DELETE FROM admins WHERE id=?");
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    private List<long[]> dbListAdmins() {
        List<long[]> list = new ArrayList<>();
        try {
            ResultSet rs = db.createStatement().executeQuery(
                "SELECT id FROM admins ORDER BY added");
            while (rs.next()) list.add(new long[]{rs.getLong("id")});
            rs.close();
        } catch (SQLException ignored) {}
        return list;
    }

    // ─── FILMLAR ────────────────────────────────────────
    /**
     * Film saqlash — avval o'chirib, keyin barcha qismlarni yozamiz.
     * Xotiraga (movies map) ham saqlanadi — tez o'qish uchun.
     */
    private boolean dbSaveMovie(String code, String title, List<String> fileIds) {
        try {
            // Avval o'chiramiz
            PreparedStatement del = db.prepareStatement("DELETE FROM movies WHERE code=?");
            del.setString(1, code); del.executeUpdate();

            // Keyin yozamiz
            PreparedStatement ins = db.prepareStatement(
                "INSERT INTO movies (code,file_id,title,part) VALUES (?,?,?,?)");
            for (int i = 0; i < fileIds.size(); i++) {
                ins.setString(1, code);
                ins.setString(2, fileIds.get(i));
                ins.setString(3, title != null && !title.isBlank() ? title : code);
                ins.setInt(4, i + 1);
                ins.addBatch();
            }
            ins.executeBatch();

            // Xotiraga ham saqlaymiz
            movies.put(code, new Movie(code,
                title != null && !title.isBlank() ? title : code,
                new ArrayList<>(fileIds)));

            System.out.println("✅ Saqlandi: " + code + " | " + title + " | " + fileIds.size() + " qism");
            return true;
        } catch (SQLException e) {
            System.err.println("❌ dbSaveMovie xato: " + e.getMessage());
            return false;
        }
    }

    private boolean dbDelMovie(String code) {
        try {
            PreparedStatement ps = db.prepareStatement("DELETE FROM movies WHERE code=?");
            ps.setString(1, code);
            int r = ps.executeUpdate();
            movies.remove(code);
            return r > 0;
        } catch (SQLException e) { return false; }
    }

    private boolean dbUpdateTitle(String code, String newTitle) {
        try {
            PreparedStatement ps = db.prepareStatement("UPDATE movies SET title=? WHERE code=?");
            ps.setString(1, newTitle); ps.setString(2, code);
            int r = ps.executeUpdate();
            Movie m = movies.get(code);
            if (m != null) movies.put(code, new Movie(m.code(), newTitle, m.fileIds()));
            return r > 0;
        } catch (SQLException e) { return false; }
    }

    private boolean dbUpdateCode(String oldCode, String newCode) {
        try {
            PreparedStatement ps = db.prepareStatement("UPDATE movies SET code=? WHERE code=?");
            ps.setString(1, newCode); ps.setString(2, oldCode);
            int r = ps.executeUpdate();
            Movie m = movies.remove(oldCode);
            if (m != null) movies.put(newCode, new Movie(newCode, m.title(), m.fileIds()));
            return r > 0;
        } catch (SQLException e) { return false; }
    }

    /** DB dan barcha filmlarni xotiraga (Map) yuklaymiz — tez qidirish uchun */
    private void loadMoviesFromDb() {
        try {
            movies.clear();
            ResultSet rs = db.createStatement().executeQuery(
                "SELECT code,file_id,title,part FROM movies ORDER BY code,part ASC");
            Map<String, List<String>> fids   = new LinkedHashMap<>();
            Map<String, String>      titles  = new LinkedHashMap<>();
            while (rs.next()) {
                String code = rs.getString("code");
                fids.computeIfAbsent(code, k -> new ArrayList<>()).add(rs.getString("file_id"));
                titles.putIfAbsent(code, rs.getString("title"));
            }
            rs.close();
            for (String code : fids.keySet())
                movies.put(code, new Movie(code, titles.get(code), fids.get(code)));
            System.out.println("✅ " + movies.size() + " ta film xotiraga yuklandi.");
        } catch (SQLException e) {
            System.err.println("loadMovies xato: " + e.getMessage());
        }
    }

    private List<Movie> searchMovies(String kw) {
        String q = kw.toLowerCase();
        return movies.values().stream()
            .filter(m -> m.code().toLowerCase().contains(q)
                      || m.title().toLowerCase().contains(q))
            .limit(10).collect(Collectors.toList());
    }

    // ─── FOYDALANUVCHILAR ───────────────────────────────
    private void dbSaveUser(long id, String uname, String fname) {
        try {
            PreparedStatement ps = db.prepareStatement("""
                INSERT INTO users (id,username,first_name) VALUES (?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    username=excluded.username,
                    first_name=excluded.first_name,
                    last_seen=CURRENT_TIMESTAMP""");
            ps.setLong(1, id); ps.setString(2, uname); ps.setString(3, fname);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private int countUsers() {
        try {
            ResultSet rs = db.createStatement().executeQuery("SELECT COUNT(*) FROM users");
            int c = rs.next() ? rs.getInt(1) : 0; rs.close(); return c;
        } catch (SQLException e) { return 0; }
    }

    private List<Long> getAllUserIds() {
        List<Long> ids = new ArrayList<>();
        try {
            ResultSet rs = db.createStatement().executeQuery("SELECT id FROM users");
            while (rs.next()) ids.add(rs.getLong(1)); rs.close();
        } catch (SQLException ignored) {}
        return ids;
    }

    // ═══════════════════════════════════════════════════
    //  ADMIN TEKSHIRUVI
    // ═══════════════════════════════════════════════════
    private boolean isSuperAdmin(long uid) { return uid == SUPER_ADMIN_ID; }

    private boolean isAdmin(long uid, String uname) {
        if (uid == SUPER_ADMIN_ID) return true;
        if (uname != null && uname.equalsIgnoreCase(SUPER_ADMIN_USERNAME)) return true;
        return isAdminInDb(uid);
    }

    // ═══════════════════════════════════════════════════
    //  HOLAT
    // ═══════════════════════════════════════════════════
    enum State {
        NONE,
        WAITING_CODE, WAITING_VIDEOS,
        WAITING_BROADCAST,
        WAITING_DEL_CODE,
        WAITING_SEARCH,
        WAITING_CH_NAME, WAITING_CH_ID, WAITING_CH_LINK,
        WAITING_ADD_ADMIN,
        WAITING_EDIT_CODE, WAITING_EDIT_FIELD, WAITING_EDIT_VALUE, WAITING_EDIT_VIDEOS
    }

    private final Map<String, Movie>      movies      = new ConcurrentHashMap<>();
    private final Map<Long, State>        states      = new ConcurrentHashMap<>();
    private final Map<Long, String>       pendingCode = new ConcurrentHashMap<>();
    private final Map<Long, String>       pendingTitle= new ConcurrentHashMap<>();
    private final Map<Long, List<String>> pendingVids = new ConcurrentHashMap<>();
    private final Map<Long, String>       pendingData = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════
    //  BOT
    // ═══════════════════════════════════════════════════
    @Override public String getBotUsername() { return BOT_USERNAME; }
    @Override public String getBotToken()    { return BOT_TOKEN; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Join request — avtomatik qabul
            if (update.hasChatJoinRequest()) {
                ChatJoinRequest req = update.getChatJoinRequest();
                ApproveChatJoinRequest a = new ApproveChatJoinRequest();
                a.setChatId(String.valueOf(req.getChat().getId()));
                a.setUserId(req.getUser().getId());
                try { execute(a); } catch (TelegramApiException ignored) {}
                return;
            }

            if (update.hasCallbackQuery()) { handleCallback(update.getCallbackQuery()); return; }
            if (!update.hasMessage()) return;

            Message msg  = update.getMessage();
            long chatId  = msg.getChatId();
            long userId  = msg.getFrom().getId();
            String uname = msg.getFrom().getUserName();
            String fname = msg.getFrom().getFirstName();

            // Foydalanuvchini saqlaymiz
            dbSaveUser(userId, uname, fname);

            // Admin bo'lsa — admin handleriga
            if (isAdmin(userId, uname) && handleAdmin(msg)) return;

            // /start
            if (msg.hasText() && msg.getText().startsWith("/start")) {
                if (isAdmin(userId, uname)) {
                    sendMsg(chatId, buildAdminHeader(userId, fname), adminKb(userId));
                } else if (!subscribedAll(userId)) {
                    sendSubMsg(chatId, userId);
                } else {
                    sendMsg(chatId,
                        "👋 <b>Salom, " + escHtml(fname) + "!</b>\n\n"
                        + "🎬 Film kodini yuboring:\n<i>Masalan: <code>0001</code></i>",
                        userKb());
                }
                return;
            }

            // A'zolik tekshiruvi
            if (!subscribedAll(userId)) { sendSubMsg(chatId, userId); return; }

            // Foydalanuvchi xabarlari
            if (msg.hasText()) {
                String txt = msg.getText().trim();
                if (txt.equals("🔍 Qidirish")) {
                    states.put(userId, State.WAITING_SEARCH);
                    sendText(chatId, "🔍 Film nomini yoki kodini yuboring:"); return;
                }
                if (!txt.startsWith("/")) {
                    if (states.getOrDefault(userId, State.NONE) == State.WAITING_SEARCH) {
                        states.put(userId, State.NONE);
                        handleUserSearch(chatId, txt);
                    } else {
                        sendFilm(chatId, txt);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Xato: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    //  A'ZOLIK TEKSHIRUVI
    // ═══════════════════════════════════════════════════
    private boolean subscribedAll(long uid) {
        if (isAdmin(uid, null)) return true;
        for (Channel ch : channels)
            if (!subscribed(uid, ch.id())) return false;
        return true;
    }

    private boolean subscribed(long uid, long chId) {
        try {
            GetChatMember r = new GetChatMember();
            r.setChatId(String.valueOf(chId)); r.setUserId(uid);
            String s = execute(r).getStatus();
            return s.equals("member") || s.equals("administrator")
                || s.equals("creator") || s.equals("restricted");
        } catch (TelegramApiException e) {
            // Kanal tekshirib bo'lmasa — o'tkazib yuboramiz
            return true;
        }
    }

    private void sendSubMsg(long chatId, long uid) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Channel ch : channels) {
            boolean ok = subscribed(uid, ch.id());
            InlineKeyboardButton b = new InlineKeyboardButton();
            b.setText(ok ? "✅ " + ch.name() : "📢 " + ch.name() + " — Obuna bo'lish");
            b.setUrl(ch.link());
            rows.add(List.of(b));
        }
        InlineKeyboardButton chk = new InlineKeyboardButton();
        chk.setText("✅ Tekshirish"); chk.setCallbackData("chk");
        rows.add(List.of(chk));

        SendMessage m = new SendMessage();
        m.setChatId(String.valueOf(chatId));
        m.setText("⛔ <b>Botdan foydalanish uchun kanallarga obuna bo'ling!</b>\n\n"
            + "Obuna bo'lgach <b>✅ Tekshirish</b> tugmasini bosing:");
        m.setParseMode("HTML");
        m.setReplyMarkup(new InlineKeyboardMarkup(rows));
        exec(m);
    }

    // ═══════════════════════════════════════════════════
    //  CALLBACK
    // ═══════════════════════════════════════════════════
    private void handleCallback(CallbackQuery cb) {
        long chatId  = cb.getMessage().getChatId();
        long uid     = cb.getFrom().getId();
        String uname = cb.getFrom().getUserName();
        String data  = cb.getData();

        try {
            AnswerCallbackQuery a = new AnswerCallbackQuery();
            a.setCallbackQueryId(cb.getId()); execute(a);
        } catch (Exception ignored) {}

        // Obuna tekshirish
        if ("chk".equals(data)) {
            if (subscribedAll(uid))
                sendMsg(chatId,
                    "✅ <b>Barcha kanallarga obuna bo'ldingiz!</b>\n\n🎬 Film kodini yuboring:",
                    userKb());
            else sendSubMsg(chatId, uid);
            return;
        }

        if (!isAdmin(uid, uname)) return;

        // Kanal o'chirish
        if (data.startsWith("del_ch:")) {
            long cid = Long.parseLong(data.split(":")[1]);
            sendText(chatId, dbDelChannel(cid) ? "✅ Kanal o'chirildi!" : "❌ Topilmadi.");

        // Admin o'chirish
        } else if (data.startsWith("del_admin:")) {
            long aid = Long.parseLong(data.split(":")[1]);
            if (aid == SUPER_ADMIN_ID) { sendText(chatId, "⛔ Super adminni o'chirib bo'lmaydi!"); return; }
            if (!isSuperAdmin(uid))    { sendText(chatId, "⛔ Faqat Super Admin!"); return; }
            sendText(chatId, dbDelAdmin(aid) ? "✅ Admin o'chirildi!" : "❌ Topilmadi.");

        // Film tahrirlash
        } else if (data.startsWith("edit:")) {
            String[] parts = data.split(":", 3);
            if (parts.length < 3) return;
            String field = parts[1], code = parts[2];

            if (field.equals("cancel")) {
                states.put(uid, State.NONE); clearPending(uid);
                sendMsg(chatId, "❌ Bekor qilindi.", adminKb(uid)); return;
            }
            pendingCode.put(uid, code);
            switch (field) {
                case "title" -> {
                    states.put(uid, State.WAITING_EDIT_VALUE);
                    pendingData.put(uid, "title");
                    sendText(chatId, "✏️ Yangi nomini kiriting:\n/cancel");
                }
                case "code" -> {
                    states.put(uid, State.WAITING_EDIT_VALUE);
                    pendingData.put(uid, "code");
                    sendText(chatId, "🔑 Yangi kodini kiriting:\n/cancel");
                }
                case "vids" -> {
                    Movie mv = movies.get(code);
                    pendingTitle.put(uid, mv != null ? mv.title() : code);
                    pendingVids.put(uid, new ArrayList<>());
                    states.put(uid, State.WAITING_EDIT_VIDEOS);
                    sendText(chatId,
                        "🎬 Hozirda <b>" + (mv != null ? mv.fileIds().size() : 0) + "</b> ta video.\n\n"
                        + "Yangi videolarni forward qiling.\n"
                        + "⚡ Har video kelganda avtomatik saqlanadi!\n\n/cancel");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  FILM YUBORISH
    // ═══════════════════════════════════════════════════
    private void sendFilm(long chatId, String code) {
        Movie mv = movies.get(code.toUpperCase());
        if (mv == null) {
            List<Movie> res = searchMovies(code);
            if (!res.isEmpty()) {
                StringBuilder sb = new StringBuilder(
                    "❌ <b>\"" + escHtml(code) + "\"</b> kodi topilmadi.\n\n"
                    + "🔍 <b>O'xshashlar:</b>\n━━━━━━━━━━━━━━━━\n");
                for (Movie m : res) {
                    sb.append("🎬 <code>").append(m.code()).append("</code> — ")
                      .append(escHtml(m.title()));
                    if (m.fileIds().size() > 1)
                        sb.append(" (").append(m.fileIds().size()).append(" qism)");
                    sb.append("\n");
                }
                sendText(chatId, sb.toString());
            } else {
                sendText(chatId,
                    "❌ <b>Film topilmadi!</b>\n\n"
                    + "💡 <i>\"🔍 Qidirish\" orqali nom bilan ham qidiring!</i>");
            }
            return;
        }

        int total = mv.fileIds().size();

        if (total > 1)
            sendText(chatId,
                "🎬 <b>" + escHtml(mv.title()) + "</b>\n"
                + "📽 Jami <b>" + total + "</b> qism yuborilmoqda...");

        for (int i = 0; i < mv.fileIds().size(); i++) {
            // FAQAT birinchi qismda to'liq caption, qolganlarida qism raqami
            String caption = total == 1
                ? "🎬 <b>" + escHtml(mv.title()) + "</b>"
                : "🎬 <b>" + escHtml(mv.title()) + "</b>\n📌 <b>" + (i + 1) + "-qism</b> / " + total;

            SendVideo v = new SendVideo();
            v.setChatId(String.valueOf(chatId));
            v.setVideo(new InputFile(mv.fileIds().get(i)));
            v.setCaption(caption);
            v.setParseMode("HTML");
            try { execute(v); }
            catch (TelegramApiException e) { System.err.println("video xato: " + e.getMessage()); }

            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
    }

    private void handleUserSearch(long chatId, String kw) {
        List<Movie> res = searchMovies(kw);
        if (res.isEmpty()) {
            sendText(chatId, "❌ <b>\"" + escHtml(kw) + "\"</b> topilmadi."); return;
        }
        StringBuilder sb = new StringBuilder("🔍 <b>Natijalar:</b>\n━━━━━━━━━━━━━━━━\n");
        for (Movie m : res) {
            sb.append("🎬 <code>").append(m.code()).append("</code> — ").append(escHtml(m.title()));
            if (m.fileIds().size() > 1)
                sb.append(" (").append(m.fileIds().size()).append(" qism)");
            sb.append("\n");
        }
        sb.append("\n📌 Kodini yuboring!");
        sendText(chatId, sb.toString());
    }

    // ═══════════════════════════════════════════════════
    //  ADMIN HANDLER
    // ═══════════════════════════════════════════════════
    private boolean handleAdmin(Message msg) {
        long aid     = msg.getFrom().getId();
        String uname = msg.getFrom().getUserName();
        String fname = msg.getFrom().getFirstName();
        String txt   = msg.hasText() ? msg.getText().trim() : "";
        State  st    = states.getOrDefault(aid, State.NONE);

        // /start — admin paneli
        if (txt.startsWith("/start")) {
            states.put(aid, State.NONE); clearPending(aid);
            sendMsg(aid, buildAdminHeader(aid, fname), adminKb(aid)); return true;
        }

        if (txt.equals("/admin") || txt.equals("👑 Admin panel")) {
            states.put(aid, State.NONE); clearPending(aid);
            sendMsg(aid, buildAdminHeader(aid, fname), adminKb(aid)); return true;
        }

        if (txt.equals("/myid")) {
            sendText(aid, "🆔 ID: <code>" + aid + "</code>\n👤 @"
                + (uname != null ? uname : "yo'q") + "\n🏅 "
                + (isSuperAdmin(aid) ? "👑 Super Admin" : "🛡 Admin"));
            return true;
        }

        // ── Film qo'shish ────────────────────────────────
        if (txt.equals("/addmovie") || txt.equals("➕ Film qo'shish")) {
            states.put(aid, State.WAITING_CODE);
            pendingVids.put(aid, new ArrayList<>());
            pendingTitle.remove(aid);
            sendText(aid,
                "🎬 <b>Film qo'shish</b>\n\n"
                + "1️⃣ Film kodini kiriting:\n<i>Masalan: <code>0001</code></i>\n\n"
                + "/cancel — bekor qilish");
            return true;
        }

        // ── Film o'chirish ───────────────────────────────
        if (txt.equals("❌ Film o'chirish") || txt.startsWith("/delmovie")) {
            if (txt.startsWith("/delmovie ")) {
                String code = txt.substring(10).trim().toUpperCase();
                sendMsg(aid, dbDelMovie(code)
                    ? "✅ <code>" + code + "</code> — o'chirildi."
                    : "❌ <code>" + code + "</code> — topilmadi.", adminKb(aid));
                return true;
            }
            states.put(aid, State.WAITING_DEL_CODE);
            sendText(aid, "❌ <b>Film o'chirish</b>\n\nKodini yuboring:\n\n/cancel");
            return true;
        }

        // ── Film tahrirlash ──────────────────────────────
        if (txt.equals("✏️ Film tahrirlash") || txt.equals("/editmovie")) {
            states.put(aid, State.WAITING_EDIT_CODE);
            sendText(aid, "✏️ <b>Film tahrirlash</b>\n\nFilm kodini yuboring:\n\n/cancel");
            return true;
        }

        // ── Filmlar ro'yxati ─────────────────────────────
        if (txt.equals("/listmovies") || txt.equals("📋 Filmlar ro'yxati")) {
            if (movies.isEmpty()) { sendText(aid, "📭 Bazada filmlar yo'q."); return true; }
            StringBuilder sb = new StringBuilder(
                "🎬 <b>Filmlar ro'yxati</b> (" + movies.size() + " ta)\n━━━━━━━━━━━━━━━━\n");
            int i = 1;
            for (Movie m : movies.values()) {
                sb.append(i++).append(". <code>").append(m.code())
                  .append("</code> — ").append(escHtml(m.title()));
                if (m.fileIds().size() > 1)
                    sb.append(" (").append(m.fileIds().size()).append(" qism)");
                sb.append("\n");
                if (i > 50) {
                    sb.append("...va yana ").append(movies.size() - 50).append(" ta");
                    break;
                }
            }
            sendText(aid, sb.toString()); return true;
        }

        // ── Qidirish ─────────────────────────────────────
        if (txt.equals("/search") || txt.equals("🔍 Film qidirish")) {
            states.put(aid, State.WAITING_SEARCH);
            sendText(aid, "🔍 Nom yoki kodini yuboring:\n\n/cancel"); return true;
        }

        // ── Statistika ───────────────────────────────────
        if (txt.equals("/stats") || txt.equals("📊 Statistika")) {
            sendText(aid, String.format("""
                📊 <b>Statistika</b>
                ━━━━━━━━━━━━━━━━━━━━━━━
                👥 Foydalanuvchilar: <b>%d</b> ta
                🎬 Filmlar:          <b>%d</b> ta
                📺 Kanallar:         <b>%d</b> ta
                🛡 Adminlar:         <b>%d</b> ta
                ━━━━━━━━━━━━━━━━━━━━━━━
                🤖 @%s""",
                countUsers(), movies.size(), channels.size(),
                dbListAdmins().size() + 1, BOT_USERNAME));
            return true;
        }

        // ── Reklama ──────────────────────────────────────
        if (txt.equals("/broadcast") || txt.equals("📣 Reklama yuborish")) {
            states.put(aid, State.WAITING_BROADCAST);
            sendText(aid, "📣 <b>Reklama</b>\n\nXabarni yuboring:\n\n/cancel"); return true;
        }

        // ── Super Admin: Kanallar ────────────────────────
        if (txt.equals("📺 Kanallar") || txt.equals("/channels")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            showChannelsList(aid); return true;
        }
        if (txt.equals("➕ Kanal qo'shish") || txt.equals("/addchannel")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            states.put(aid, State.WAITING_CH_NAME);
            sendText(aid, "📺 <b>Kanal qo'shish</b>\n\n1️⃣ Kanal nomini kiriting:\n\n/cancel");
            return true;
        }
        if (txt.equals("❌ Kanal o'chirish") || txt.equals("/delchannel")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            showChannelsDeleteMenu(aid); return true;
        }

        // ── Super Admin: Adminlar ────────────────────────
        if (txt.equals("👥 Adminlar") || txt.equals("/admins")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            showAdminsList(aid); return true;
        }
        if (txt.equals("➕ Admin qo'shish") || txt.equals("/addadmin")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            states.put(aid, State.WAITING_ADD_ADMIN);
            sendText(aid, "👥 <b>Admin qo'shish</b>\n\nAdmin Telegram ID sini yuboring:\n\n/cancel");
            return true;
        }
        if (txt.equals("❌ Admin o'chirish") || txt.equals("/deladmin")) {
            if (!isSuperAdmin(aid)) { sendText(aid, "⛔ Faqat Super Admin!"); return true; }
            showAdminsDeleteMenu(aid); return true;
        }

        // ── Bekor qilish ─────────────────────────────────
        if (txt.equals("/cancel") || txt.equals("🚫 Bekor qilish")) {
            states.put(aid, State.NONE); clearPending(aid);
            sendMsg(aid, "❌ Bekor qilindi.", adminKb(aid)); return true;
        }

        // ════════════════════════════════════════════════
        //  VIDEO KELDI
        // ════════════════════════════════════════════════
        if (msg.hasVideo() && (st == State.WAITING_VIDEOS || st == State.WAITING_EDIT_VIDEOS)) {
            String fid  = msg.getVideo().getFileId();
            String code = pendingCode.get(aid);

            if (code == null) {
                sendText(aid, "⚠️ Avval film kodini kiriting. /addmovie");
                return true;
            }

            // Nom FAQAT birinchi videoning caption'idan olinadi
            if (!pendingTitle.containsKey(aid)) {
                String cap   = msg.getCaption();
                String title = (cap != null && !cap.isBlank()) ? cap.trim() : code;
                pendingTitle.put(aid, title);
            }

            List<String> vids = pendingVids.computeIfAbsent(aid, k -> new ArrayList<>());
            vids.add(fid);

            boolean ok = dbSaveMovie(code, pendingTitle.get(aid), new ArrayList<>(vids));

            sendText(aid, ok
                ? "✅ <b>" + vids.size() + "-qism</b> saqlandi!\n"
                    + "🔑 Kod: <code>" + code + "</code>\n"
                    + "🎬 Nomi: <b>" + escHtml(pendingTitle.get(aid)) + "</b>\n\n"
                    + "📌 Yana video forward qiling yoki /cancel"
                : "⚠️ Saqlashda xato! Qayta urinib ko'ring.");
            return true;
        }

        // ════════════════════════════════════════════════
        //  MATN HOLATLARI
        // ════════════════════════════════════════════════
        if (msg.hasText() && !txt.startsWith("/")) {

            // Kod kutmoqda
            if (st == State.WAITING_CODE) {
                String code = txt.toUpperCase().replaceAll("[^A-Z0-9_\\-]", "");
                if (code.isEmpty()) {
                    sendText(aid, "❌ Noto'g'ri kod! Faqat harf va raqam."); return true;
                }
                pendingCode.put(aid, code);
                pendingVids.put(aid, new ArrayList<>());
                pendingTitle.remove(aid);
                states.put(aid, State.WAITING_VIDEOS);
                sendText(aid,
                    "✅ Kod: <code>" + code + "</code>\n\n"
                    + "2️⃣ Filmni forward qiling:\n"
                    + "<i>💡 Birinchi videoning caption'i film nomi bo'ladi.\n"
                    + "Qolgan qismlarning caption'i hisobga olinmaydi.</i>\n\n"
                    + "⚡ Har video kelganda avtomatik saqlanadi!\n\n/cancel");
                return true;
            }

            // Video kutilayotganda matn keldi
            if (st == State.WAITING_VIDEOS || st == State.WAITING_EDIT_VIDEOS) {
                sendText(aid, "⚠️ Video fayl forward qiling!\n/cancel"); return true;
            }

            // O'chirish kodi
            if (st == State.WAITING_DEL_CODE) {
                String code = txt.toUpperCase().trim();
                sendMsg(aid, dbDelMovie(code)
                    ? "✅ <code>" + code + "</code> — o'chirildi!"
                    : "❌ <code>" + code + "</code> — topilmadi!", adminKb(aid));
                states.put(aid, State.NONE); return true;
            }

            // Qidiruv
            if (st == State.WAITING_SEARCH) {
                states.put(aid, State.NONE);
                List<Movie> res = searchMovies(txt);
                if (res.isEmpty()) {
                    sendMsg(aid, "❌ \"" + escHtml(txt) + "\" topilmadi.", adminKb(aid));
                } else {
                    StringBuilder sb = new StringBuilder("🔍 <b>Natijalar:</b>\n━━━━━━━━━━━━━━━━\n");
                    for (Movie m : res) {
                        sb.append("🎬 <code>").append(m.code()).append("</code> — ").append(escHtml(m.title()));
                        if (m.fileIds().size() > 1)
                            sb.append(" (").append(m.fileIds().size()).append(" qism)");
                        sb.append("\n");
                    }
                    sendMsg(aid, sb.toString(), adminKb(aid));
                }
                return true;
            }

            // Kanal qo'shish — nom
            if (st == State.WAITING_CH_NAME) {
                pendingData.put(aid, txt); states.put(aid, State.WAITING_CH_ID);
                sendText(aid, "✅ Nom: <b>" + escHtml(txt) + "</b>\n\n"
                    + "2️⃣ Kanal ID:\n<code>-1001234567890</code>\n\n/cancel");
                return true;
            }
            // Kanal qo'shish — ID
            if (st == State.WAITING_CH_ID) {
                try {
                    long cid = Long.parseLong(txt.trim());
                    pendingCode.put(aid, String.valueOf(cid));
                    states.put(aid, State.WAITING_CH_LINK);
                    sendText(aid, "✅ ID: <code>" + cid + "</code>\n\n"
                        + "3️⃣ Invite link:\n<code>https://t.me/+xxxxx</code>\n\n/cancel");
                } catch (NumberFormatException e) {
                    sendText(aid, "❌ Noto'g'ri ID! Manfiy son bo'lishi kerak.\nMasalan: <code>-1001234567890</code>");
                }
                return true;
            }
            // Kanal qo'shish — link
            if (st == State.WAITING_CH_LINK) {
                String name = pendingData.get(aid);
                long cid    = Long.parseLong(pendingCode.get(aid));
                boolean ok  = dbAddChannel(name, cid, txt.trim());
                sendMsg(aid, ok
                    ? "✅ <b>Kanal qo'shildi!</b>\n📺 " + escHtml(name) + "\n🆔 <code>" + cid + "</code>"
                    : "❌ Xatolik! Bu ID allaqachon mavjud bo'lishi mumkin.", adminKb(aid));
                states.put(aid, State.NONE); clearPending(aid); return true;
            }

            // Admin qo'shish
            if (st == State.WAITING_ADD_ADMIN) {
                try {
                    long newId = Long.parseLong(txt.trim());
                    if (newId == SUPER_ADMIN_ID) {
                        sendText(aid, "⚠️ Super admin allaqachon mavjud!");
                    } else {
                        boolean ok = dbAddAdmin(newId, null, aid);
                        sendMsg(aid, ok
                            ? "✅ <b>Admin qo'shildi!</b>\n🆔 <code>" + newId + "</code>"
                            : "❌ Xatolik!", adminKb(aid));
                    }
                } catch (NumberFormatException e) {
                    sendText(aid, "❌ Noto'g'ri ID! Masalan: <code>123456789</code>");
                }
                states.put(aid, State.NONE); return true;
            }

            // Tahrirlash: kod qabul
            if (st == State.WAITING_EDIT_CODE) {
                String code = txt.toUpperCase().trim();
                Movie mv = movies.get(code);
                if (mv == null) {
                    sendText(aid, "❌ <code>" + code + "</code> topilmadi. Qayta kiriting:");
                    return true;
                }
                pendingCode.put(aid, code);
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton b1 = new InlineKeyboardButton();
                b1.setText("✏️ Nomini o'zgartirish"); b1.setCallbackData("edit:title:" + code);
                InlineKeyboardButton b2 = new InlineKeyboardButton();
                b2.setText("🔑 Kodini o'zgartirish"); b2.setCallbackData("edit:code:" + code);
                InlineKeyboardButton b3 = new InlineKeyboardButton();
                b3.setText("🎬 Videolarni almashtirish"); b3.setCallbackData("edit:vids:" + code);
                InlineKeyboardButton b4 = new InlineKeyboardButton();
                b4.setText("🚫 Bekor qilish"); b4.setCallbackData("edit:cancel:" + code);
                rows.add(List.of(b1, b2)); rows.add(List.of(b3)); rows.add(List.of(b4));
                SendMessage m = new SendMessage();
                m.setChatId(String.valueOf(aid));
                m.setText("✏️ <b>" + code + "</b>\n🎬 " + escHtml(mv.title())
                    + "\n📽 " + mv.fileIds().size() + " qism\n\nNimani tahrirlaysiz?");
                m.setParseMode("HTML"); m.setReplyMarkup(new InlineKeyboardMarkup(rows)); exec(m);
                states.put(aid, State.WAITING_EDIT_FIELD); return true;
            }

            // Tahrirlash: qiymat qabul
            if (st == State.WAITING_EDIT_VALUE) {
                String field = pendingData.get(aid);
                String code  = pendingCode.get(aid);
                if (field != null && code != null) {
                    if (field.equals("title")) {
                        sendMsg(aid, dbUpdateTitle(code, txt)
                            ? "✅ Nom: <b>\"" + escHtml(txt) + "\"</b>"
                            : "❌ Xatolik!", adminKb(aid));
                    } else if (field.equals("code")) {
                        String nc = txt.toUpperCase().replaceAll("[^A-Z0-9_\\-]", "");
                        sendMsg(aid, dbUpdateCode(code, nc)
                            ? "✅ <code>" + code + "</code> → <code>" + nc + "</code>"
                            : "❌ Xatolik!", adminKb(aid));
                    }
                }
                states.put(aid, State.NONE); clearPending(aid); return true;
            }
        }

        // ── Reklama yuborish ─────────────────────────────
        if (st == State.WAITING_BROADCAST) {
            states.put(aid, State.NONE);
            List<Long> uids = getAllUserIds();
            sendText(aid, "⏳ " + uids.size() + " ta foydalanuvchiga yuborilmoqda...");
            int ok = 0, fail = 0;
            for (Long uid : uids) {
                if (isAdmin(uid, null)) continue;
                try {
                    ForwardMessage fw = new ForwardMessage();
                    fw.setChatId(String.valueOf(uid));
                    fw.setFromChatId(String.valueOf(aid));
                    fw.setMessageId(msg.getMessageId());
                    execute(fw); ok++;
                    Thread.sleep(50);
                } catch (Exception e) { fail++; }
            }
            sendMsg(aid, "✅ <b>Reklama tugadi!</b>\n✅ Yuborildi: <b>" + ok
                + "</b>\n❌ Xato: <b>" + fail + "</b>", adminKb(aid));
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════
    //  YORDAMCHI METODLAR
    // ═══════════════════════════════════════════════════
    private String buildAdminHeader(long uid, String fname) {
        return "╔════════════════════════════╗\n"
            + "║  " + (isSuperAdmin(uid) ? "👑 SUPER ADMIN" : "🛡 ADMIN") + "\n"
            + "║  👤 " + escHtml(fname != null ? fname : "Admin") + "\n"
            + "║  🆔 " + uid + "\n"
            + "╚════════════════════════════╝\n\nQuyidagi bo'limlardan birini tanlang:";
    }

    private void showChannelsList(long aid) {
        if (channels.isEmpty()) { sendText(aid, "📭 Kanallar yo'q."); return; }
        StringBuilder sb = new StringBuilder(
            "📺 <b>Kanallar</b> (" + channels.size() + " ta)\n━━━━━━━━━━━━━━━━\n");
        int i = 1;
        for (Channel ch : channels)
            sb.append(i++).append(". <b>").append(escHtml(ch.name())).append("</b>\n")
              .append("   🆔 <code>").append(ch.id()).append("</code>\n")
              .append("   🔗 ").append(ch.link()).append("\n\n");
        sendText(aid, sb.toString());
    }

    private void showChannelsDeleteMenu(long aid) {
        if (channels.isEmpty()) { sendText(aid, "📭 Kanal yo'q."); return; }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Channel ch : channels) {
            InlineKeyboardButton b = new InlineKeyboardButton();
            b.setText("❌ " + ch.name());
            b.setCallbackData("del_ch:" + ch.id());
            rows.add(List.of(b));
        }
        SendMessage m = new SendMessage(); m.setChatId(String.valueOf(aid));
        m.setText("📺 <b>Qaysi kanalni o'chirasiz?</b>");
        m.setParseMode("HTML"); m.setReplyMarkup(new InlineKeyboardMarkup(rows)); exec(m);
    }

    private void showAdminsList(long aid) {
        StringBuilder sb = new StringBuilder("👥 <b>Adminlar</b>\n━━━━━━━━━━━━━━━━\n");
        sb.append("1. 👑 @").append(SUPER_ADMIN_USERNAME)
          .append("\n   🆔 <code>").append(SUPER_ADMIN_ID).append("</code>\n\n");
        List<long[]> list = dbListAdmins();
        int i = 2;
        for (long[] a : list)
            sb.append(i++).append(". 🛡 <code>").append(a[0]).append("</code>\n");
        sendText(aid, sb.toString());
    }

    private void showAdminsDeleteMenu(long aid) {
        List<long[]> list = dbListAdmins();
        if (list.isEmpty()) { sendMsg(aid, "📭 Qo'shimcha adminlar yo'q.", adminKb(aid)); return; }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (long[] a : list) {
            InlineKeyboardButton b = new InlineKeyboardButton();
            b.setText("❌ Admin: " + a[0]);
            b.setCallbackData("del_admin:" + a[0]);
            rows.add(List.of(b));
        }
        SendMessage m = new SendMessage(); m.setChatId(String.valueOf(aid));
        m.setText("👥 <b>Qaysi adminni o'chirasiz?</b>");
        m.setParseMode("HTML"); m.setReplyMarkup(new InlineKeyboardMarkup(rows)); exec(m);
    }

    private void clearPending(long aid) {
        pendingCode.remove(aid); pendingTitle.remove(aid);
        pendingVids.remove(aid); pendingData.remove(aid);
    }

    /** HTML injection oldini olish */
    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ═══════════════════════════════════════════════════
    //  KLAVIATURALAR
    // ═══════════════════════════════════════════════════
    private ReplyKeyboardMarkup userKb() {
        KeyboardRow r = new KeyboardRow();
        r.add(new KeyboardButton("🎬 Film qidirish"));
        r.add(new KeyboardButton("🔍 Qidirish"));
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(List.of(r));
        kb.setResizeKeyboard(true); return kb;
    }

    private ReplyKeyboardMarkup adminKb(long uid) {
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton("➕ Film qo'shish"));
        r1.add(new KeyboardButton("❌ Film o'chirish")); rows.add(r1);
        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton("✏️ Film tahrirlash"));
        r2.add(new KeyboardButton("📋 Filmlar ro'yxati")); rows.add(r2);
        KeyboardRow r3 = new KeyboardRow();
        r3.add(new KeyboardButton("🔍 Film qidirish"));
        r3.add(new KeyboardButton("📊 Statistika")); rows.add(r3);
        KeyboardRow r4 = new KeyboardRow();
        r4.add(new KeyboardButton("📣 Reklama yuborish"));
        r4.add(new KeyboardButton("👑 Admin panel")); rows.add(r4);
        if (isSuperAdmin(uid)) {
            KeyboardRow r5 = new KeyboardRow();
            r5.add(new KeyboardButton("📺 Kanallar"));
            r5.add(new KeyboardButton("❌ Kanal o'chirish")); rows.add(r5);
            KeyboardRow r6 = new KeyboardRow();
            r6.add(new KeyboardButton("➕ Kanal qo'shish"));
            r6.add(new KeyboardButton("➕ Admin qo'shish")); rows.add(r6);
            KeyboardRow r7 = new KeyboardRow();
            r7.add(new KeyboardButton("👥 Adminlar"));
            r7.add(new KeyboardButton("❌ Admin o'chirish")); rows.add(r7);
        }
        KeyboardRow r8 = new KeyboardRow();
        r8.add(new KeyboardButton("🚫 Bekor qilish")); rows.add(r8);
        ReplyKeyboardMarkup kb = new ReplyKeyboardMarkup(rows);
        kb.setResizeKeyboard(true); return kb;
    }

    private void sendText(long chatId, String text) {
        SendMessage m = new SendMessage();
        m.setChatId(String.valueOf(chatId)); m.setText(text); m.setParseMode("HTML"); exec(m);
    }

    private void sendMsg(long chatId, String text, ReplyKeyboard kb) {
        SendMessage m = new SendMessage();
        m.setChatId(String.valueOf(chatId)); m.setText(text);
        m.setParseMode("HTML"); m.setReplyMarkup(kb); exec(m);
    }

    private void exec(BotApiMethod<?> method) {
        try { execute(method); }
        catch (TelegramApiException e) { System.err.println("exec xato: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════
    //  ISHGA TUSHIRISH
    // ═══════════════════════════════════════════════════
    public static void main(String[] args) {
        System.out.println("🎬 KinoBot ishga tushmoqda...");
        try {
            Main bot = new Main();
            bot.initDb();
            bot.loadMoviesFromDb();
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(bot);
            System.out.println("✅ KinoBot ishga tushdi! @" + BOT_USERNAME);
        } catch (TelegramApiException e) {
            System.err.println("❌ Xato: " + e.getMessage());
        }
    }
}
