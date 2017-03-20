package org.jenkinsci.deprecatedusage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

public class UpdateCenter {
    private final URL updateCenterUrl;
    private final JenkinsFile core;
    private final List<JenkinsFile> plugins = new ArrayList<>();

    public UpdateCenter(URL updateCenterUrl)
            throws IOException, ParserConfigurationException, SAXException {
        super();
        this.updateCenterUrl = updateCenterUrl;
        final String string = getUpdateCenterJson();

        final JSONObject jsonRoot = new JSONObject(string);
        final JSONObject jsonCore = jsonRoot.getJSONObject("core");
        core = parseCore(jsonCore);

        final JSONObject jsonPlugins = jsonRoot.getJSONObject("plugins");
        for (final Object pluginId : jsonPlugins.keySet()) {
            final JSONObject jsonPlugin = jsonPlugins.getJSONObject(pluginId.toString());
            final JenkinsFile plugin = parse(jsonPlugin);
            plugins.add(plugin);
        }
        final Comparator<JenkinsFile> comparator = new Comparator<JenkinsFile>() {
            @Override
            public int compare(JenkinsFile o1, JenkinsFile o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        };
        Collections.sort(plugins, comparator);
    }

    private String getUpdateCenterJson() throws IOException, MalformedURLException {
        final byte[] updateCenterData = new HttpGet(updateCenterUrl).read();
        final String string = new String(updateCenterData, StandardCharsets.UTF_8)
                .replace("updateCenter.post(", "");
        return string;
    }

    private JenkinsFile parseCore(JSONObject jsonObject) throws MalformedURLException, JSONException {
        return new JenkinsFile(jsonObject.getString("name"), System.getProperty("coreVersionOverride", jsonObject.getString("version")),
                jsonObject.getString("url"), null);
    }

    private JenkinsFile parse(JSONObject jsonObject) throws MalformedURLException, JSONException {
        final String wiki;
        if (jsonObject.has("wiki")) {
            wiki = jsonObject.getString("wiki");
        } else {
            wiki = null;
        }
        return new JenkinsFile(jsonObject.getString("name"), jsonObject.getString("version"),
                jsonObject.getString("url"), wiki);
    }

    public void download() throws Exception {
        // download in parallel
        core.startDownloadIfNotExists();
        for (final JenkinsFile plugin : plugins) {
            plugin.startDownloadIfNotExists();
        }
        // wait end of downloads
        core.waitDownload();
        for (final JenkinsFile plugin : new ArrayList<>(plugins)) {
            try {
                plugin.waitDownload();
            } catch (final FileNotFoundException e) {
                Log.log(e.toString());
                plugins.remove(plugin);
            }
        }
    }

    public JenkinsFile getCore() {
        return core;
    }

    public List<JenkinsFile> getPlugins() {
        return plugins;
    }
}
