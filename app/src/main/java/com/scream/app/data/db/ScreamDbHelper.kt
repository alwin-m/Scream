package com.scream.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.scream.app.model.ChatMessage
import com.scream.app.model.MessageKind
import com.scream.app.model.Post
import com.scream.app.model.Room
import com.scream.app.model.User
import org.json.JSONArray
import org.json.JSONObject

class ScreamDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    data class PostReactionChange(val previous: String?, val current: String?)

    companion object {
        private const val DB_NAME = "scream_mesh_db.db"
        private const val DB_VERSION = 3

        // Tables
        private const val TABLE_POSTS = "posts"
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_ROOMS = "rooms"
        private const val TABLE_BITCHAT_IDENTITY = "bitchat_identity"
        private const val TABLE_PEER_IDENTITY_MAP = "peer_identity_map"
        private const val TABLE_NOISE_SESSIONS = "noise_sessions"
        private const val TABLE_POST_REACTIONS = "post_reactions"
        private const val TABLE_POST_RESHARES = "post_reshares"
        private const val TABLE_POST_VIEWS = "post_views"
        private const val TABLE_HIDDEN_POSTS = "hidden_posts"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Table Posts
        db.execSQL("""
            CREATE TABLE $TABLE_POSTS (
                id TEXT PRIMARY KEY,
                user_id TEXT,
                user_alias TEXT,
                user_avatar TEXT,
                body TEXT,
                timestamp TEXT,
                created_at INTEGER,
                media_path TEXT,
                media_mime_type TEXT,
                audio_duration_ms INTEGER,
                views INTEGER,
                likes INTEGER,
                dislikes INTEGER,
                reshares INTEGER,
                is_liked INTEGER,
                is_disliked INTEGER,
                is_reshared INTEGER,
                liked_by_json TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_posts_created ON $TABLE_POSTS(created_at DESC)")

        // Table Messages
        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                id TEXT PRIMARY KEY,
                room_id TEXT,
                sender_id TEXT,
                sender_alias TEXT,
                sender_avatar TEXT,
                body TEXT,
                timestamp TEXT,
                created_at INTEGER,
                expires_at INTEGER,
                pinned_until INTEGER,
                is_mine INTEGER,
                kind TEXT,
                audio_path TEXT,
                audio_duration_ms INTEGER,
                media_path TEXT,
                media_mime_type TEXT,
                reply_to_id TEXT,
                reply_to_sender TEXT,
                reply_to_body TEXT,
                reactions_json TEXT,
                is_bookmarked INTEGER,
                route_json TEXT,
                delivery_status TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_messages_room_time ON $TABLE_MESSAGES(room_id, created_at ASC)")
        db.execSQL("CREATE INDEX idx_messages_expires ON $TABLE_MESSAGES(expires_at)")

        // Table Rooms
        db.execSQL("""
            CREATE TABLE $TABLE_ROOMS (
                id TEXT PRIMARY KEY,
                name TEXT,
                icon TEXT,
                preview TEXT,
                member_count INTEGER,
                is_private INTEGER,
                admin_id TEXT,
                members_json TEXT
            )
        """.trimIndent())

        createProtocolTables(db)
        createSocialTables(db)
    }

    /**
     * Create the tables needed for multi-protocol (BitChat) support.
     * Separated so the same DDL can be called from both [onCreate] and [onUpgrade].
     */
    private fun createProtocolTables(db: SQLiteDatabase) {
        // BitChat keypair identity (local device)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_BITCHAT_IDENTITY (
                id INTEGER PRIMARY KEY,
                public_key BLOB NOT NULL,
                encrypted_private_key BLOB NOT NULL,
                sender_id BLOB NOT NULL,
                nostr_pubkey TEXT,
                created_at INTEGER
            )
        """.trimIndent())

        // Cross-protocol peer identity mapping
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_PEER_IDENTITY_MAP (
                scream_id TEXT,
                bitchat_sender_id BLOB,
                bitchat_pubkey BLOB,
                nostr_pubkey TEXT,
                display_alias TEXT,
                last_seen INTEGER,
                PRIMARY KEY (scream_id, bitchat_sender_id)
            )
        """.trimIndent())

        // Noise session cache (for persistent E2E sessions across restarts)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_NOISE_SESSIONS (
                peer_id TEXT PRIMARY KEY,
                local_keypair_id INTEGER,
                session_state BLOB,
                created_at INTEGER,
                last_used INTEGER
            )
        """.trimIndent())
    }

    private fun createSocialTables(db: SQLiteDatabase) {
        // Social Likes and Dislikes (unique per user_id + post_id)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_POST_REACTIONS (
                user_id TEXT NOT NULL,
                post_id TEXT NOT NULL,
                reaction_type TEXT NOT NULL,
                created_at INTEGER,
                PRIMARY KEY (user_id, post_id)
            )
        """.trimIndent())

        // Social Reshares (unique per user_id + original_post_id)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_POST_RESHARES (
                user_id TEXT NOT NULL,
                original_post_id TEXT NOT NULL,
                created_at INTEGER,
                PRIMARY KEY (user_id, original_post_id)
            )
        """.trimIndent())

        // Unique Post Views (unique per user_id + post_id)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_POST_VIEWS (
                user_id TEXT NOT NULL,
                post_id TEXT NOT NULL,
                viewed_at INTEGER,
                PRIMARY KEY (user_id, post_id)
            )
        """.trimIndent())

        // Hidden Posts ("Remove from My Feed")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_HIDDEN_POSTS (
                post_id TEXT PRIMARY KEY,
                hidden_at INTEGER
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 → v2: add multi-protocol tables (non-destructive)
            createProtocolTables(db)
        }
        if (oldVersion < 3) {
            // v2 → v3: add social reaction tables and feed control
            createSocialTables(db)
        }
    }

    // ── Posts DB Operations ──────────────────────────────────────────────────

    fun insertOrUpdatePost(post: Post) {
        val values = ContentValues().apply {
            put("id", post.id)
            put("user_id", post.user.id)
            put("user_alias", post.user.alias)
            put("user_avatar", post.user.avatar)
            put("body", post.body)
            put("timestamp", post.timestamp)
            put("created_at", post.createdAt)
            put("media_path", post.mediaBase64) // Stored as path reference or payload
            put("media_mime_type", post.mediaMimeType)
            put("audio_duration_ms", post.audioDurationMs)
            put("views", post.views)
            put("likes", post.likes)
            put("dislikes", post.dislikes)
            put("reshares", post.reshares)
            put("is_liked", if (post.isLiked) 1 else 0)
            put("is_disliked", if (post.isDisliked) 1 else 0)
            put("is_reshared", if (post.isReshared) 1 else 0)
            put("liked_by_json", JSONArray(post.likedBy).toString())
        }
        writableDatabase.insertWithOnConflict(TABLE_POSTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllPosts(limit: Int = 100): List<Post> {
        val list = mutableListOf<Post>()
        val cursor = readableDatabase.query(
            TABLE_POSTS, null, null, null, null, null, "created_at DESC", "$limit"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val likedByJsonStr = c.getString(c.getColumnIndexOrThrow("liked_by_json")) ?: "[]"
                val likedByArr = runCatching { JSONArray(likedByJsonStr) }.getOrDefault(JSONArray())
                val likedByList = (0 until likedByArr.length()).map { likedByArr.getString(it) }

                list.add(
                    Post(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        user = User(
                            id = c.getString(c.getColumnIndexOrThrow("user_id")),
                            alias = c.getString(c.getColumnIndexOrThrow("user_alias")),
                            avatar = c.getString(c.getColumnIndexOrThrow("user_avatar"))
                        ),
                        body = c.getString(c.getColumnIndexOrThrow("body")),
                        timestamp = c.getString(c.getColumnIndexOrThrow("timestamp")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        mediaBase64 = c.getString(c.getColumnIndexOrThrow("media_path")),
                        mediaMimeType = c.getString(c.getColumnIndexOrThrow("media_mime_type")),
                        audioDurationMs = c.getLong(c.getColumnIndexOrThrow("audio_duration_ms")),
                        views = c.getInt(c.getColumnIndexOrThrow("views")),
                        likes = c.getInt(c.getColumnIndexOrThrow("likes")),
                        dislikes = c.getInt(c.getColumnIndexOrThrow("dislikes")),
                        reshares = c.getInt(c.getColumnIndexOrThrow("reshares")),
                        isLiked = c.getInt(c.getColumnIndexOrThrow("is_liked")) == 1,
                        isDisliked = c.getInt(c.getColumnIndexOrThrow("is_disliked")) == 1,
                        isReshared = c.getInt(c.getColumnIndexOrThrow("is_reshared")) == 1,
                        likedBy = likedByList
                    )
                )
            }
        }
        return list
    }

    fun deletePost(postId: String) {
        writableDatabase.delete(TABLE_POSTS, "id = ?", arrayOf(postId))
    }

    /** Stores the desired reaction state so duplicate mesh envelopes are idempotent. */
    fun setPostReaction(userId: String, postId: String, reactionType: String?): PostReactionChange {
        val db = writableDatabase
        var previous: String? = null
        db.beginTransaction()
        try {
            db.query(
                TABLE_POST_REACTIONS,
                arrayOf("reaction_type"),
                "user_id = ? AND post_id = ?",
                arrayOf(userId, postId), null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) previous = cursor.getString(0)
            }
            if (previous != reactionType) {
                if (reactionType == null) {
                    db.delete(TABLE_POST_REACTIONS, "user_id = ? AND post_id = ?", arrayOf(userId, postId))
                } else {
                    val values = ContentValues().apply {
                        put("user_id", userId)
                        put("post_id", postId)
                        put("reaction_type", reactionType)
                        put("created_at", System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(TABLE_POST_REACTIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return PostReactionChange(previous, reactionType)
    }

    // ── Messages DB Operations ───────────────────────────────────────────────

    fun insertOrUpdateMessage(roomId: String, msg: ChatMessage, defaultTtlMs: Long = 48L * 3600 * 1000) {
        val rxJson = JSONObject().apply {
            msg.reactions.forEach { (emoji, aliases) ->
                put(emoji, JSONArray(aliases))
            }
        }.toString()

        val values = ContentValues().apply {
            put("id", msg.id)
            put("room_id", roomId)
            put("sender_id", msg.sender.id)
            put("sender_alias", msg.sender.alias)
            put("sender_avatar", msg.sender.avatar)
            put("body", msg.body)
            put("timestamp", msg.timestamp)
            put("created_at", msg.createdAt)
            put("expires_at", msg.expiresAt(defaultTtlMs))
            put("pinned_until", msg.pinnedUntil)
            put("is_mine", if (msg.isMine) 1 else 0)
            put("kind", msg.kind.name)
            put("audio_path", msg.audioBase64)
            put("audio_duration_ms", msg.audioDurationMs)
            put("media_path", msg.mediaBase64)
            put("media_mime_type", msg.mediaMimeType)
            put("reply_to_id", msg.replyToId)
            put("reply_to_sender", msg.replyToSender)
            put("reply_to_body", msg.replyToBody)
            put("reactions_json", rxJson)
            put("is_bookmarked", if (msg.isBookmarked) 1 else 0)
            put("route_json", JSONArray(msg.route).toString())
            put("delivery_status", msg.deliveryStatus)
        }
        writableDatabase.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getRoomMessages(roomId: String, limit: Int = 200): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val cursor = readableDatabase.query(
            TABLE_MESSAGES, null, "room_id = ?", arrayOf(roomId), null, null, "created_at ASC", "$limit"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val rxStr = c.getString(c.getColumnIndexOrThrow("reactions_json")) ?: "{}"
                val rxObj = runCatching { JSONObject(rxStr) }.getOrDefault(JSONObject())
                val rxMap = mutableMapOf<String, List<String>>()
                val keys = rxObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = rxObj.getJSONArray(k)
                    val aliases = (0 until arr.length()).map { arr.getString(it) }
                    rxMap[k] = aliases
                }

                val routeStr = c.getString(c.getColumnIndexOrThrow("route_json")) ?: "[]"
                val routeArr = runCatching { JSONArray(routeStr) }.getOrDefault(JSONArray())
                val routeList = (0 until routeArr.length()).map { routeArr.getString(it) }

                val kindName = c.getString(c.getColumnIndexOrThrow("kind")) ?: MessageKind.TEXT.name
                val kind = runCatching { MessageKind.valueOf(kindName) }.getOrDefault(MessageKind.TEXT)

                list.add(
                    ChatMessage(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sender = User(
                            id = c.getString(c.getColumnIndexOrThrow("sender_id")),
                            alias = c.getString(c.getColumnIndexOrThrow("sender_alias")),
                            avatar = c.getString(c.getColumnIndexOrThrow("sender_avatar"))
                        ),
                        body = c.getString(c.getColumnIndexOrThrow("body")),
                        timestamp = c.getString(c.getColumnIndexOrThrow("timestamp")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        pinnedUntil = c.getLong(c.getColumnIndexOrThrow("pinned_until")).takeIf { it > 0 },
                        isMine = c.getInt(c.getColumnIndexOrThrow("is_mine")) == 1,
                        kind = kind,
                        audioBase64 = c.getString(c.getColumnIndexOrThrow("audio_path")),
                        audioDurationMs = c.getLong(c.getColumnIndexOrThrow("audio_duration_ms")),
                        mediaBase64 = c.getString(c.getColumnIndexOrThrow("media_path")),
                        mediaMimeType = c.getString(c.getColumnIndexOrThrow("media_mime_type")),
                        replyToId = c.getString(c.getColumnIndexOrThrow("reply_to_id")),
                        replyToSender = c.getString(c.getColumnIndexOrThrow("reply_to_sender")),
                        replyToBody = c.getString(c.getColumnIndexOrThrow("reply_to_body")),
                        reactions = rxMap,
                        isBookmarked = c.getInt(c.getColumnIndexOrThrow("is_bookmarked")) == 1,
                        route = routeList,
                        deliveryStatus = c.getString(c.getColumnIndexOrThrow("delivery_status")) ?: "Delivered"
                    )
                )
            }
        }
        return list
    }

    fun deleteMessage(messageId: String) {
        writableDatabase.delete(TABLE_MESSAGES, "id = ?", arrayOf(messageId))
    }

    fun purgeExpiredContent(defaultCutoff: Long, privateCutoff: Long, now: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Delete expired posts
            db.delete(TABLE_POSTS, "created_at < ?", arrayOf(defaultCutoff.toString()))
            // Delete expired messages
            db.delete(TABLE_MESSAGES, "expires_at <= ? AND (pinned_until IS NULL OR pinned_until <= ?)", arrayOf(now.toString(), now.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Rooms DB Operations ──────────────────────────────────────────────────

    fun insertOrUpdateRoom(room: Room) {
        val values = ContentValues().apply {
            put("id", room.id)
            put("name", room.name)
            put("icon", room.icon)
            put("preview", room.preview)
            put("member_count", room.memberCount)
            put("is_private", if (room.isPrivate) 1 else 0)
            put("admin_id", room.adminId)
            put("members_json", JSONArray(room.members).toString())
        }
        writableDatabase.insertWithOnConflict(TABLE_ROOMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllRooms(): List<Room> {
        val list = mutableListOf<Room>()
        val cursor = readableDatabase.query(TABLE_ROOMS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val roomId = c.getString(c.getColumnIndexOrThrow("id"))
                val memberStr = c.getString(c.getColumnIndexOrThrow("members_json")) ?: "[]"
                val memberArr = runCatching { JSONArray(memberStr) }.getOrDefault(JSONArray())
                val memberList = (0 until memberArr.length()).map { memberArr.getString(it) }
                val roomMsgs = getRoomMessages(roomId)

                list.add(
                    Room(
                        id = roomId,
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        icon = c.getString(c.getColumnIndexOrThrow("icon")),
                        preview = c.getString(c.getColumnIndexOrThrow("preview")),
                        memberCount = c.getInt(c.getColumnIndexOrThrow("member_count")),
                        isPrivate = c.getInt(c.getColumnIndexOrThrow("is_private")) == 1,
                        adminId = c.getString(c.getColumnIndexOrThrow("admin_id")),
                        messages = roomMsgs,
                        members = memberList
                    )
                )
            }
        }
        return list
    }

    fun deleteRoom(roomId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ROOMS, "id = ?", arrayOf(roomId))
            db.delete(TABLE_MESSAGES, "room_id = ?", arrayOf(roomId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
