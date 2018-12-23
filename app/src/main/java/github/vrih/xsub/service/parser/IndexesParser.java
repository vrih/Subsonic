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
package github.vrih.xsub.service.parser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import github.vrih.xsub.R;
import github.vrih.xsub.domain.Artist;
import github.vrih.xsub.domain.Indexes;
import github.vrih.xsub.domain.MusicDirectory;
import github.vrih.xsub.util.Constants;
import github.vrih.xsub.util.ProgressListener;
import github.vrih.xsub.util.Util;

/**
 * @author Sindre Mehus
 */
public class IndexesParser extends MusicDirectoryEntryParser {
    private static final String TAG = IndexesParser.class.getSimpleName();

    public IndexesParser(Context context, int instance) {
        super(context, instance);
    }

    public Indexes parse(Reader reader, ProgressListener progressListener) throws Exception {
        long t0 = System.currentTimeMillis();
        init(reader);

        List<Artist> artists = new ArrayList<>();
        List<Artist> shortcuts = new ArrayList<>();
		List<MusicDirectory.Entry> entries = new ArrayList<>();
        Long lastModified = null;
        int eventType;
        String ignoredArticles = null;
        boolean changed = false;
		Map<String, Artist> artistList = new HashMap<>();

        do {
            eventType = nextParseEvent();
            if (eventType == XmlPullParser.START_TAG) {
                String name = getElementName();
                if ("indexes".equals(name) || "artists".equals(name)) {
                    changed = true;
                    lastModified = getLong("lastModified");
					ignoredArticles = get("ignoredArticles");
                } else if ("index".equals(name)) {

                } else if ("artist".equals(name)) {
                    Artist artist = new Artist(get("id"), get("name"));
					artist.setStarred(get("starred") != null);
					// Combine the id's for the two artists
					if(artistList.containsKey(artist.getName())) {
						Artist originalArtist = artistList.get(artist.getName());
						if(originalArtist.isStarred()) {
							artist.setStarred(true);
						}
						// TODO: should ID be mutable?
						//originalArtist.setId(originalArtist.getId() + ";" + artist.getId());
					} else {
						artistList.put(artist.getName(), artist);
						artists.add(artist);
					}

                    if (artists.size() % 10 == 0) {
                        String msg = getContext().getResources().getString(R.string.parser_artist_count, artists.size());
                        updateProgress(progressListener, msg);
                    }
                } else if ("shortcut".equals(name)) {
                    Artist shortcut = new Artist(get("id"), get("name"));
					shortcut.setStarred(get("starred") != null);
                    shortcuts.add(shortcut);
				} else if("child".equals(name)) {
					MusicDirectory.Entry entry = parseEntry("");
					entries.add(entry);
				} else if ("error".equals(name)) {
                    handleError();
                }
            }
        } while (eventType != XmlPullParser.END_DOCUMENT);

        validate();
		
		if(ignoredArticles != null) {
			SharedPreferences.Editor prefs = Util.getPreferences(context).edit();
			prefs.putString(Constants.CACHE_KEY_IGNORE, ignoredArticles);
			prefs.apply();
		}

        if (!changed) {
            return null;
        }

        long t1 = System.currentTimeMillis();
        Log.d(TAG, "Got " + artists.size() + " artist(s) in " + (t1 - t0) + "ms.");

        String msg = getContext().getResources().getString(R.string.parser_artist_count, artists.size());
        updateProgress(progressListener, msg);

        return new Indexes(shortcuts, artists, entries);
    }
}
