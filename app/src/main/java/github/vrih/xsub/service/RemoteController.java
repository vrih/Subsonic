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
	
	Copyright 2009 (C) Sindre Mehus
*/

package github.vrih.xsub.service;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import github.vrih.xsub.domain.MusicDirectory;
import github.vrih.xsub.domain.RemoteStatus;
import github.vrih.xsub.util.Constants;
import github.vrih.xsub.util.Util;

public abstract class RemoteController {
	private static final String TAG = RemoteController.class.getSimpleName();
	final DownloadService downloadService;
	boolean nextSupported = false;

	RemoteController(DownloadService downloadService) {
		this.downloadService = downloadService;
	}

	public abstract void create(boolean playing, int seconds);
	public abstract void start();
	public abstract void stop();
	public abstract void next();
	public abstract void previous();

	public abstract void shutdown();
	
	public abstract void updatePlaylist();

    public abstract void insertPlaylist(List<DownloadFile> songs, int index);

	public abstract void appendPlaylist(List<DownloadFile> songs);

	public abstract void changePosition(int seconds);

    public abstract void changeTrack(int index, List<DownloadFile> downloadList, int position);

    public abstract void setCurrentPlaying(int index);

    public abstract void changeTrack(int index, DownloadFile song, int position);
	// Really is abstract, just don't want to require RemoteController's support it
	public void changeNextTrack(DownloadFile song) {}
	public boolean isNextSupported() {
		if(Util.getPreferences(downloadService).getBoolean(Constants.PREFERENCES_KEY_CAST_GAPLESS_PLAYBACK, true)) {
			return this.nextSupported;
		} else {
			return false;
		}
	}
	public abstract void setVolume(int volume);
	public abstract void updateVolume(boolean up);
	public abstract double getVolume();
	public boolean isSeekable() {
		return true;
	}
	
	public abstract int getRemotePosition();
	public int getRemoteDuration() {
		return 0;
	}

	abstract class RemoteTask {
		abstract RemoteStatus execute() throws Exception;

		@Override
		public String toString() {
			return getClass().getSimpleName();
		}
	}

	static class TaskQueue {
		private final LinkedBlockingQueue<RemoteTask> queue = new LinkedBlockingQueue<>();

		void add(RemoteTask jukeboxTask) {
			queue.add(jukeboxTask);
		}

		RemoteTask take() throws InterruptedException {
			return queue.take();
		}

		void remove(Class<? extends RemoteTask> clazz) {
			try {
				Iterator<RemoteTask> iterator = queue.iterator();
				while (iterator.hasNext()) {
					RemoteTask task = iterator.next();
					if (clazz.equals(task.getClass())) {
						iterator.remove();
					}
				}
			} catch (Throwable x) {
				Log.w(TAG, "Failed to clean-up task queue.", x);
			}
		}
	}

	String getStreamUrl(MusicService musicService, DownloadFile downloadFile) throws Exception {
		MusicDirectory.Entry song = downloadFile.getSong();

		String url;
		// In offline mode or playing offline song
		if(downloadFile.isStream()) {
			url = downloadFile.getStream();
		} else {
			if(song.isVideo()) {
				url = musicService.getHlsUrl(song.getId(), downloadFile.getBitRate(), downloadService);
			} else {
				url = musicService.getMusicUrl(downloadService, song, downloadFile.getBitRate());
			}
		}

		return url;
	}
}
