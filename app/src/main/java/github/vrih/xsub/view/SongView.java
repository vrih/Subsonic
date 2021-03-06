/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.vrih.xsub.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

import androidx.core.content.ContextCompat;
import github.vrih.xsub.R;
import github.vrih.xsub.domain.MusicDirectory;
import github.vrih.xsub.domain.PodcastEpisode;
import github.vrih.xsub.service.DownloadFile;
import github.vrih.xsub.service.DownloadService;
import github.vrih.xsub.util.DrawableTint;
import github.vrih.xsub.util.SongDBHandler;
import github.vrih.xsub.util.ThemeUtil;
import github.vrih.xsub.util.Util;

/**
 * Used to display songs in a {@code ListView}.
 *
 * @author Sindre Mehus
 */
public class SongView extends UpdateView2<MusicDirectory.Entry, Boolean> {

	private final TextView trackTextView;
	private final TextView titleTextView;
	private TextView playingTextView;
	private final TextView artistTextView;
	private final TextView durationTextView;
	private final TextView statusTextView;
	private final ImageView statusImageView;
	private final ImageView bookmarkButton;
	private final ImageView playedButton;
	private final View bottomRowView;

	private DownloadService downloadService;
	private long revision = -1;
	private DownloadFile downloadFile;
	private boolean dontChangeDownloadFile = false;

	private boolean playing = false;
	private boolean rightImage = false;
	private int moreImage = 0;
	private boolean isWorkDone = false;
	private boolean isSaved = false;
	private File partialFile;
	private boolean partialFileExists = false;
	private boolean loaded = false;
	private boolean isBookmarked = false;
	private boolean isBookmarkedShown = false;
	private boolean showPodcast = false;
	private boolean isPlayed = false;
	private boolean isPlayedShown = false;
	private boolean showAlbum = false;

	public SongView(Context context) {
		super(context);
		LayoutInflater.from(context).inflate(R.layout.song_list_item, this, true);

		trackTextView = findViewById(R.id.song_track);
		titleTextView = findViewById(R.id.song_title);
		artistTextView = findViewById(R.id.song_artist);
		durationTextView = findViewById(R.id.song_duration);
		statusTextView = findViewById(R.id.song_status);
		statusImageView = findViewById(R.id.song_status_icon);
		ratingBar = findViewById(R.id.song_rating);
		starButton = findViewById(R.id.song_star);
		starButton.setFocusable(false);
		bookmarkButton = (ImageButton) findViewById(R.id.song_bookmark);
		bookmarkButton.setFocusable(false);
		playedButton = (ImageButton) findViewById(R.id.song_played);
		moreButton = findViewById(R.id.item_more);
		bottomRowView = findViewById(R.id.song_bottom);
	}

	protected void setObjectImpl(MusicDirectory.Entry song, Boolean checkable) {
		this.checkable = checkable;

		StringBuilder artist = new StringBuilder(40);

		boolean isPodcast = song instanceof PodcastEpisode;
		if(!song.isVideo() || isPodcast) {
			if(isPodcast) {
				PodcastEpisode episode = (PodcastEpisode) song;
				if(showPodcast && episode.getArtist() != null) {
					artist.append(episode.getArtist());
				}

				String date = episode.getDate();
				if(date != null) {
					if(artist.length() != 0) {
						artist.append(" - ");
					}
					artist.append(Util.formatDate(context, date, false));
				}
			}
			else if(song.getArtist() != null) {
				if(showAlbum) {
					artist.append(song.getAlbum());
				} else {
					artist.append(song.getArtist());
				}
			}

			if(isPodcast) {
				String status = ((PodcastEpisode) song).getStatus();
				int statusRes = -1;

				if("error".equals(status)) {
					statusRes = R.string.song_details_error;
				} else if("skipped".equals(status)) {
					statusRes = R.string.song_details_skipped;
				} else if("downloading".equals(status)) {
					statusRes = R.string.song_details_downloading;
				}

				if(statusRes != -1) {
					artist.append(" (");
					artist.append(getContext().getString(statusRes));
					artist.append(")");
				}
			}

			durationTextView.setText(Util.formatDuration(song.getDuration()));
			bottomRowView.setVisibility(View.VISIBLE);
		} else {
			bottomRowView.setVisibility(View.GONE);
			statusTextView.setText(Util.formatDuration(song.getDuration()));
		}

		String title = song.getTitle();
		Integer track = song.getTrack();
		if(song.getCustomOrder() != null) {
			track = song.getCustomOrder();
		}
		TextView newPlayingTextView;
		if(track != null && Util.getDisplayTrack(context)) {
			trackTextView.setText(String.format("%02d", track));
			trackTextView.setVisibility(View.VISIBLE);
			newPlayingTextView = trackTextView;
		} else {
			trackTextView.setVisibility(View.GONE);
			newPlayingTextView = titleTextView;
		}

		if(newPlayingTextView != playingTextView || playingTextView == null) {
			if(playing) {
				playingTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				playing = false;
			}

			playingTextView = newPlayingTextView;
		}

		titleTextView.setText(title);
		artistTextView.setText(artist);

		this.setBackgroundColor(0x00000000);
		ratingBar.setVisibility(View.GONE);
		rating = 0;

		revision = -1;
		loaded = false;
		dontChangeDownloadFile = false;
	}

	public void setDownloadFile(DownloadFile downloadFile) {
		this.downloadFile = downloadFile;
		dontChangeDownloadFile = true;
	}

	public DownloadFile getDownloadFile() {
		return downloadFile;
	}

	@Override
	protected void updateBackground() {
		if (downloadService == null) {
			downloadService = DownloadService.getInstance();
			if(downloadService == null) {
				return;
			}
		}

		long newRevision = downloadService.getDownloadListUpdateRevision();
		if((revision != newRevision && !dontChangeDownloadFile) || downloadFile == null) {
			downloadFile = downloadService.forSong(item);
			revision = newRevision;
		}

		isWorkDone = downloadFile.isWorkDone();
		isSaved = downloadFile.isSaved();
		partialFile = downloadFile.getPartialFile();
		partialFileExists = partialFile.exists();
		isStarred = item.isStarred();
		isBookmarked = item.getBookmark() != null;
		isRated = item.getRating();

		// Check if needs to load metadata: check against all fields that we know are null in offline mode
		if(item.getBitRate() == null && item.getDuration() == null && item.getDiscNumber() == null && isWorkDone) {
			item.loadMetadata(downloadFile.getCompleteFile());
			loaded = true;
		}

		if(item instanceof PodcastEpisode || item.isAudioBook() || item.isPodcast()) {
			isPlayed = new SongDBHandler(context).hasBeenCompleted(item);
		}
	}

	@Override
	protected void update() {
		if(loaded) {
			setObjectImpl(item, item2);
		}
		if (downloadService == null || downloadFile == null) {
			return;
		}

		if(item.isStarred()) {
			if(!starred) {
				if(starButton.getDrawable() == null) {
					starButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_toggle_star));
				}
				starButton.setVisibility(View.VISIBLE);
				starred = true;
			}
		} else {
			if(starred) {
				starButton.setVisibility(View.GONE);
				starred = false;
			}
		}

		if (isWorkDone) {
			int moreImage = isSaved ? R.drawable.download_pinned : R.drawable.download_cached;
			if(moreImage != this.moreImage) {
				moreButton.setImageResource(moreImage);
				this.moreImage = moreImage;
			}
		} else if(this.moreImage != R.drawable.download_none) {
			moreButton.setImageResource(R.drawable.download_none);
			this.moreImage = R.drawable.download_none;
		}

		if (downloadFile.isDownloading() && downloadFile.isDownloadRunning() && partialFileExists) {
			double percentage = (partialFile.length() * 100.0) / downloadFile.getEstimatedSize();
			percentage = Math.min(percentage, 100);
			statusTextView.setText((int)percentage + " %");
			if(!rightImage) {
				statusImageView.setVisibility(View.VISIBLE);
				rightImage = true;
			}
		} else if(rightImage) {
			statusTextView.setText(null);
			statusImageView.setVisibility(View.GONE);
			rightImage = false;
		}

		boolean playing = Util.equals(downloadService.getCurrentPlaying(), downloadFile);
		if (playing) {
			if(!this.playing) {
				this.playing = true;
				// TODO: Match colour to text colour
				Drawable startIcon = ContextCompat.getDrawable(context, R.drawable.media_start);
				startIcon.setTint(ContextCompat.getColor(context, R.color.design_default_color_on_secondary));
				playingTextView.setCompoundDrawablesWithIntrinsicBounds(startIcon, null, null, null);
			}
		} else {
			if(this.playing) {
				this.playing = playing;
				playingTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
			}
		}

		if(isBookmarked) {
			if(!isBookmarkedShown) {
				if(bookmarkButton.getDrawable() == null) {
					bookmarkButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_menu_bookmark_selected));
				}

				bookmarkButton.setVisibility(View.VISIBLE);
				isBookmarkedShown = true;
			}
		} else {
			if(isBookmarkedShown) {
				bookmarkButton.setVisibility(View.GONE);
				isBookmarkedShown = false;
			}
		}

		if(isPlayed) {
			if(!isPlayedShown) {
				if(playedButton.getDrawable() == null) {
					playedButton.setImageDrawable(DrawableTint.getTintedDrawable(context, R.drawable.ic_toggle_played));
				}

				playedButton.setVisibility(View.VISIBLE);
				isPlayedShown = true;
			}
		} else {
			if(isPlayedShown) {
				playedButton.setVisibility(View.GONE);
				isPlayedShown = false;
			}
		}

		if(isRated != rating) {
			if(isRated > 1) {
				if(rating <= 1) {
					ratingBar.setVisibility(View.VISIBLE);
				}

				ratingBar.setNumStars(isRated);
				ratingBar.setRating(isRated);
			} else {
				if(rating > 1) {
					ratingBar.setVisibility(View.GONE);
				}
			}

			// Still highlight red if a 1-star
			if(isRated == 1) {
				this.setBackgroundColor(Color.RED);

				String theme = ThemeUtil.getTheme(context);
				if("black".equals(theme)) {
					this.getBackground().setAlpha(80);
				} else if("dark".equals(theme) || "holo".equals(theme)) {
					this.getBackground().setAlpha(60);
				} else {
					this.getBackground().setAlpha(20);
				}
			} else if(rating == 1) {
				this.setBackgroundColor(0x00000000);
			}

			rating = isRated;
		}
	}

	public MusicDirectory.Entry getEntry() {
		return item;
	}

	public void setShowPodcast(boolean showPodcast) {
		this.showPodcast = showPodcast;
	}

	public void setShowAlbum(boolean showAlbum) {
		this.showAlbum = showAlbum;
	}
}
