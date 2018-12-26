package de.berlios.vch.parser.ard;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.i18n.ResourceBundleLoader;
import de.berlios.vch.i18n.ResourceBundleProvider;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;

@Component
@Provides
public class ARDMediathekParser implements IWebParser, ResourceBundleProvider {

    @Requires
    private LogService logger;

    public static final String ID = ARDMediathekParser.class.getName();
    public static final String BASE_URI = "https://www.ardmediathek.de";
    public static final String CHARSET = "UTF-8";
    private static final String ROOT_PAGE = "dummy://localhost/" + ID;

    private ProgramPageParser programPageParser = new ProgramPageParser();

    private BundleContext ctx;
    private ResourceBundle resourceBundle;

    public static Map<String, String> HTTP_HEADERS = new HashMap<String, String>();
    static {
        HTTP_HEADERS.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:64.0) Gecko/20100101 Firefox/64.0");
        HTTP_HEADERS.put("Accept-Language", "de-de,de;q=0.8,en-us;q=0.5,en;q=0.3");
    }

    public ARDMediathekParser(BundleContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        String url = BASE_URI + "/ard/shows";
        OverviewPage root = new OverviewPage();
        root.setParser(getId());
        root.setTitle("Sendungen A-Z");
        root.setUri(new URI(ROOT_PAGE));

        String content = HttpUtils.get(url, HTTP_HEADERS, CHARSET);
        Elements abisz = HtmlParserUtils.getTags(content, "div[id][class~=gridlist]");
        for (Element character : abisz) {
            String gridlist = character.html();
            OverviewPage charPage = new OverviewPage();
            charPage.setParser(getId());
            charPage.setTitle(HtmlParserUtils.getText(gridlist, "h2"));
            charPage.setUri(new URI("dummy://" + character.attr("id")));
            root.getPages().add(charPage);

            Elements programs = HtmlParserUtils.getTags(gridlist, "a[title]");
            for (Element p : programs) {
                String phtml = p.html();
                OverviewPage program = new OverviewPage();
                program.setParser(getId());
                String title = p.attr("title");
                String channel = HtmlParserUtils.getText(phtml, "h4");
                program.setTitle(title + " - " + channel);
                program.setUri(new URI(BASE_URI + p.attr("href")));
                charPage.getPages().add(program);
            }
        }

        return root;
    }

    @Override
    public String getTitle() {
        return "ARD Mediathek";
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IOverviewPage) {
            String pageUri = page.getUri().toString();
            if (pageUri.startsWith("dummy")) {
                // return the page
            } else if (pageUri.contains("shows")) {
                page = programPageParser.parse((IOverviewPage) page, getResourceBundle());
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

    @Override
    public ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            try {
                logger.log(LogService.LOG_DEBUG, "Loading resource bundle for " + getClass().getSimpleName());
                resourceBundle = ResourceBundleLoader.load(ctx, Locale.getDefault());
            } catch (IOException e) {
                logger.log(LogService.LOG_ERROR, "Couldn't load resource bundle", e);
            }
        }
        return resourceBundle;
    }
}