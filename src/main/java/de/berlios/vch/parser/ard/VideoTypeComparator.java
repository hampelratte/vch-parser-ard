package de.berlios.vch.parser.ard;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import de.berlios.vch.parser.ard.VideoItemPageParser.VideoType;
import de.berlios.vch.parser.ard.VideoItemPageParser.VideoType.FORMAT;

/**
 * Compares two videos according to their type and quality
 *
 * @author <a href="mailto:hampelratte@users.berlios.de">hampelratte@users.berlios.de</a>
 */
public class VideoTypeComparator implements Comparator<VideoType> {

    private Map<FORMAT, Integer> typePriorities = new HashMap<FORMAT, Integer>();

    public VideoTypeComparator() {
        typePriorities.put(FORMAT.MP4, 0);
        typePriorities.put(FORMAT.HLS, 1);
    }

    @Override
    public int compare(VideoType vt1, VideoType vt2) {
        if (typePriorities.get(vt1.getFormat()) > typePriorities.get(vt2.getFormat())) {
            return -1;
        } else if (typePriorities.get(vt1.getFormat()) < typePriorities.get(vt2.getFormat())) {
            return 1;
        }

        return vt2.getQuality() - vt1.getQuality();
    }
}
