/*
	This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2015 (C) Scott Jackson
*/

package github.vrih.xsub.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import github.vrih.xsub.domain.MusicDirectory;
import github.vrih.xsub.service.DownloadFile;

public class SongDBHandler extends SQLiteOpenHelper {
	private SongDBHandler dbHandler;

	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "SongsDB";

	private static final String TABLE_SONGS = "RegisteredSongs";
	private static final String SONGS_ID = "id";
	private static final String SONGS_SERVER_KEY = "serverKey";
	private static final String SONGS_SERVER_ID = "serverId";
	private static final String SONGS_COMPLETE_PATH = "completePath";
	private static final String SONGS_LAST_PLAYED = "lastPlayed";
	private static final String SONGS_LAST_COMPLETED = "lastCompleted";

	private final Context context;

	public SongDBHandler(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.context = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_SONGS + " ( " +
				SONGS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
				SONGS_SERVER_KEY + " INTEGER NOT NULL, " +
				SONGS_SERVER_ID + " TEXT NOT NULL, " +
				SONGS_COMPLETE_PATH + " TEXT NOT NULL, " +
				SONGS_LAST_PLAYED + " INTEGER, " +
				SONGS_LAST_COMPLETED + " INTEGER, " +
				"UNIQUE(" + SONGS_SERVER_KEY + ", " + SONGS_SERVER_ID + "))");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_SONGS);
		this.onCreate(db);
	}

	private synchronized void addSongImpl(SQLiteDatabase db, int serverKey, String id, String absolutePath) {
		ContentValues values = new ContentValues();
		values.put(SONGS_SERVER_KEY, serverKey);
		values.put(SONGS_SERVER_ID, id);
		values.put(SONGS_COMPLETE_PATH, absolutePath);

		db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
	}

	public synchronized void addSongs(List<MusicDirectory.Entry> entries) {
		SQLiteDatabase db = this.getWritableDatabase();

		List<Pair<String, String>> pairs = new ArrayList<>();
		for(MusicDirectory.Entry entry: entries) {
			pairs.add(new Pair<>(entry.getId(), FileUtil.getSongFile(context, entry).getAbsolutePath()));
		}
		addSongs(db, pairs);

		db.close();
	}
	private synchronized void addSongs(SQLiteDatabase db, List<Pair<String, String>> entries) {
		addSongsImpl(db, Util.getRestUrlHash(context), entries);
	}
	private synchronized void addSongsImpl(SQLiteDatabase db, int serverKey, List<Pair<String, String>> entries) {
		db.beginTransaction();
		try {
			for (Pair<String, String> entry : entries) {
				ContentValues values = new ContentValues();
				values.put(SONGS_SERVER_KEY, serverKey);
				values.put(SONGS_SERVER_ID, entry.getFirst());
				values.put(SONGS_COMPLETE_PATH, entry.getSecond());

				db.insertWithOnConflict(TABLE_SONGS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
			}

			db.setTransactionSuccessful();
		} catch(Exception e) {}

		db.endTransaction();
	}

	public synchronized void setSongPlayed(DownloadFile downloadFile, boolean submission) {
		// TODO: In case of offline want to update all matches
		Pair<Integer, String> pair = getOnlineSongId(downloadFile);
		if(pair == null) {
			return;
		}
		int serverKey = pair.getFirst();
		String id = pair.getSecond();

		// Open and make sure song is in db
		SQLiteDatabase db = this.getWritableDatabase();
		addSongImpl(db, serverKey, id, downloadFile.getSaveFile().getAbsolutePath());

		// Update song's last played
		ContentValues values = new ContentValues();
		values.put(submission ? SONGS_LAST_COMPLETED : SONGS_LAST_PLAYED, System.currentTimeMillis());
		db.update(TABLE_SONGS, values, SONGS_SERVER_KEY + " = ? AND " + SONGS_SERVER_ID + " = ?", new String[]{Integer.toString(serverKey), id});
		db.close();
	}

	public boolean hasBeenCompleted(MusicDirectory.Entry entry) {
		Long[] lastPlayed = getLastPlayed(entry);
		return lastPlayed != null && lastPlayed[1] != null && lastPlayed[1] > 0;
	}
	public synchronized Long[] getLastPlayed(MusicDirectory.Entry entry) {
		return getLastPlayed(getOnlineSongId(entry));
	}
	private synchronized Long[] getLastPlayed(Pair<Integer, String> pair) {
		if(pair == null) {
			return null;
		} else {
			return getLastPlayed(pair.getFirst(), pair.getSecond());
		}
	}
	private synchronized Long[] getLastPlayed(int serverKey, String id) {
		SQLiteDatabase db = this.getReadableDatabase();

		String[] columns = {SONGS_LAST_PLAYED, SONGS_LAST_COMPLETED};
		Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_SERVER_KEY + " = ? AND " + SONGS_SERVER_ID + " = ?", new String[]{Integer.toString(serverKey), id}, null, null, null, null);

		try {
			cursor.moveToFirst();

			Long[] dates = new Long[2];
			dates[0] = cursor.getLong(0);
			dates[1] = cursor.getLong(1);
			return dates;
		} catch(Exception e) {
			return null;
		}
		finally {
			cursor.close();
			db.close();
		}
	}

	public synchronized Pair<Integer, String> getOnlineSongId(MusicDirectory.Entry entry) {
		return getOnlineSongId(Util.getRestUrlHash(context), entry.getId(), FileUtil.getSongFile(context, entry).getAbsolutePath(), !Util.isOffline(context));
	}
	private synchronized Pair<Integer, String> getOnlineSongId(DownloadFile downloadFile) {
		return getOnlineSongId(Util.getRestUrlHash(context), downloadFile.getSong().getId(), downloadFile.getSaveFile().getAbsolutePath(), !Util.isOffline(context));
	}

	public synchronized Pair<Integer, String> getOnlineSongId(int serverKey, MusicDirectory.Entry entry) {
		return getOnlineSongId(serverKey, new DownloadFile(context, entry, true));
	}
	private synchronized Pair<Integer, String> getOnlineSongId(int serverKey, DownloadFile downloadFile) {
		return getOnlineSongId(serverKey, downloadFile.getSong().getId(), downloadFile.getSaveFile().getAbsolutePath(), true);
	}
	private synchronized Pair<Integer, String> getOnlineSongId(int serverKey, String id, String savePath, boolean requireServerKey) {
		SharedPreferences prefs = Util.getPreferences(context);
		String cacheLocn = prefs.getString(Constants.PREFERENCES_KEY_CACHE_LOCATION, null);
		if(cacheLocn != null && id.contains(cacheLocn)) {
			if(requireServerKey) {
				return getIdFromPath(serverKey, savePath);
			} else {
				return getIdFromPath(savePath);
			}
		} else {
			return new Pair<>(serverKey, id);
		}
	}

	public synchronized Pair<Integer, String> getIdFromPath(String path) {
		SQLiteDatabase db = this.getReadableDatabase();

		String[] columns = {SONGS_SERVER_KEY, SONGS_SERVER_ID};
		Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_COMPLETE_PATH + " = ?", new String[] { path }, null, null, SONGS_LAST_PLAYED + " DESC", null);

		try {
			cursor.moveToFirst();
			return new Pair(cursor.getInt(0), cursor.getString(1));
		} catch(Exception e) {
			return null;
		}
		finally {
			cursor.close();
			db.close();
		}
	}
	public synchronized Pair<Integer, String> getIdFromPath(int serverKey, String path) {
		SQLiteDatabase db = this.getReadableDatabase();

		String[] columns = {SONGS_SERVER_KEY, SONGS_SERVER_ID};
		Cursor cursor = db.query(TABLE_SONGS, columns, SONGS_SERVER_KEY + " = ? AND " + SONGS_COMPLETE_PATH + " = ?", new String[] {Integer.toString(serverKey), path }, null, null, null, null);

		try {
			cursor.moveToFirst();
			return new Pair(cursor.getInt(0), cursor.getString(1));
		} catch(Exception e) {
			return null;
		}
		finally {
			cursor.close();
			db.close();
		}
	}
}
