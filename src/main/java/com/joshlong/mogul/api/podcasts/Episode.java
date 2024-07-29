package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.Publishable;
import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.io.Serializable;
import java.util.Date;

public record Episode(Long id, Podcast podcast, String title, String description, Date created, ManagedFile graphic,
		ManagedFile producedGraphic, ManagedFile producedAudio, boolean complete, Date producedAudioUpdated,
		Date producedAudioAssetsUpdated/* , Collection<Publication> publications */) implements Publishable {
	@Override
	public Serializable publicationKey() {
		return this.id();
	}
}
