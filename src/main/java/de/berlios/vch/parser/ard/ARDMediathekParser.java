package de.berlios.vch.parser.ard;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;

@Component
@Provides
public class ARDMediathekParser implements IWebParser {

    @Requires
    private LogService logger;

    public static final String ID = ARDMediathekParser.class.getName();
    public static final String BASE_URI = "http://www.ardmediathek.de";
    public static final String CHARSET = "UTF-8";
    private static final String ROOT_PAGE = "dummy://localhost/" + ID;

    private ProgramParser programParser = new ProgramParser();

    private ProgramPageParser programPageParser = new ProgramPageParser();

    private BundleContext ctx;

    private final Map<String, String> aBisZ = new TreeMap<String, String>();

    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("User-Agent", "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.2) Gecko/20090821 Gentoo Firefox/3.5.7");
        HTTP_HEADERS.put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
    }

    public final OverviewPage abz;

    public ARDMediathekParser(BundleContext ctx) {
        this.ctx = ctx;

        // initialize the root page
        aBisZ.put("0-9", BASE_URI + "/tv/sendungen-a-z?buchstabe=0-9");
        aBisZ.put("A", BASE_URI + "/tv/sendungen-a-z?buchstabe=A");
        aBisZ.put("B", BASE_URI + "/tv/sendungen-a-z?buchstabe=B");
        aBisZ.put("C", BASE_URI + "/tv/sendungen-a-z?buchstabe=C");
        aBisZ.put("D", BASE_URI + "/tv/sendungen-a-z?buchstabe=D");
        aBisZ.put("E", BASE_URI + "/tv/sendungen-a-z?buchstabe=E");
        aBisZ.put("F", BASE_URI + "/tv/sendungen-a-z?buchstabe=F");
        aBisZ.put("G", BASE_URI + "/tv/sendungen-a-z?buchstabe=G");
        aBisZ.put("H", BASE_URI + "/tv/sendungen-a-z?buchstabe=H");
        aBisZ.put("I", BASE_URI + "/tv/sendungen-a-z?buchstabe=I");
        aBisZ.put("J", BASE_URI + "/tv/sendungen-a-z?buchstabe=J");
        aBisZ.put("K", BASE_URI + "/tv/sendungen-a-z?buchstabe=K");
        aBisZ.put("L", BASE_URI + "/tv/sendungen-a-z?buchstabe=L");
        aBisZ.put("M", BASE_URI + "/tv/sendungen-a-z?buchstabe=M");
        aBisZ.put("N", BASE_URI + "/tv/sendungen-a-z?buchstabe=N");
        aBisZ.put("O", BASE_URI + "/tv/sendungen-a-z?buchstabe=O");
        aBisZ.put("P", BASE_URI + "/tv/sendungen-a-z?buchstabe=P");
        aBisZ.put("Q", BASE_URI + "/tv/sendungen-a-z?buchstabe=Q");
        aBisZ.put("R", BASE_URI + "/tv/sendungen-a-z?buchstabe=R");
        aBisZ.put("S", BASE_URI + "/tv/sendungen-a-z?buchstabe=S");
        aBisZ.put("T", BASE_URI + "/tv/sendungen-a-z?buchstabe=T");
        aBisZ.put("U", BASE_URI + "/tv/sendungen-a-z?buchstabe=U");
        aBisZ.put("V", BASE_URI + "/tv/sendungen-a-z?buchstabe=V");
        aBisZ.put("W", BASE_URI + "/tv/sendungen-a-z?buchstabe=W");
        aBisZ.put("X", BASE_URI + "/tv/sendungen-a-z?buchstabe=X");
        aBisZ.put("Y", BASE_URI + "/tv/sendungen-a-z?buchstabe=Y");
        aBisZ.put("Z", BASE_URI + "/tv/sendungen-a-z?buchstabe=Z");

        abz = new OverviewPage();
        abz.setParser(getId());
        abz.setTitle("Sendungen A-Z");

        try {
            abz.setUri(new URI(ROOT_PAGE));
            for (Entry<String, String> abzPage : aBisZ.entrySet()) {
                String title = abzPage.getKey();
                String uri = abzPage.getValue();
                OverviewPage tmp = new OverviewPage();
                tmp.setParser(getId());
                tmp.setTitle(title);
                tmp.setUri(new URI(uri));
                abz.getPages().add(tmp);
            }
        } catch (URISyntaxException e) {
            // this should never happen
            logger.log(LogService.LOG_ERROR, "Couldn't create root page", e);
        }
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        return abz;
    }

    @Override
    public String getTitle() {
        return "ARD Mediathek";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IOverviewPage) {
            String pageUri = page.getUri().toString();
            if (pageUri.contains("sendungen-a-z")) {
                page = programParser.parse((IOverviewPage) page);
            } else {
                page = programPageParser.parse((IOverviewPage) page);
            }
        } else if (page instanceof IVideoPage) {
            page = VideoItemPageParser.parse((VideoPage) page, ctx);
        }
        return page;
    }

    @Override
    public String getId() {
        return ID;
    }
}