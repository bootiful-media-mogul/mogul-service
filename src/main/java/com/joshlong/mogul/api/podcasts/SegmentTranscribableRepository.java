package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.transcription.TranscribableRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;

@Service
@Transactional
class SegmentTranscribableRepository implements TranscribableRepository<Segment> {

    private final PodcastService podcastService;

    private final ManagedFileService managedFileService;

    SegmentTranscribableRepository(PodcastService podcastService, ManagedFileService managedFileService) {
        this.podcastService = podcastService;
        this.managedFileService = managedFileService;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return Segment.class.isAssignableFrom(clazz);
    }

    @Override
    public Segment find(Serializable key) {
        return this.podcastService.getPodcastEpisodeSegmentById((Long) key);
    }

    @Override
    public Resource audio(Serializable key) {
        var segment = this.find(key);
        return this.managedFileService.read(segment.producedAudio().id());
    }

    @Override
    public void write(Serializable key, String transcript) {
        var segment = this.find(key);
//        this.podcastService.setPodcastEpisodeSegmentTranscript(segment.id(), true, transcript);
    }

}
