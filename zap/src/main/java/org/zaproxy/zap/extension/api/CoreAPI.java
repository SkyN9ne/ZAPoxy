/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2011 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.api;

import ch.csnc.extension.httpclient.SSLContextManager;
import edu.umass.cs.benchlab.har.HarEntries;
import edu.umass.cs.benchlab.har.HarLog;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.tree.TreeNode;
import javax.xml.transform.stream.StreamSource;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemWriter;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.control.Control.Mode;
import org.parosproxy.paros.core.proxy.ProxyParam;
import org.parosproxy.paros.db.DatabaseException;
import org.parosproxy.paros.db.RecordHistory;
import org.parosproxy.paros.db.TableHistory;
import org.parosproxy.paros.extension.history.ExtensionHistory;
import org.parosproxy.paros.extension.option.OptionsParamCertificate;
import org.parosproxy.paros.extension.report.ReportGenerator;
import org.parosproxy.paros.extension.report.ReportLastScan;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.model.SessionListener;
import org.parosproxy.paros.model.SiteMap;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.ConnectionParam;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMalformedHeaderException;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpResponseHeader;
import org.parosproxy.paros.network.HttpSender;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.alert.AlertAPI;
import org.zaproxy.zap.extension.alert.AlertParam;
import org.zaproxy.zap.extension.alert.ExtensionAlert;
import org.zaproxy.zap.extension.dynssl.ExtensionDynSSL;
import org.zaproxy.zap.model.SessionStructure;
import org.zaproxy.zap.model.SessionUtils;
import org.zaproxy.zap.model.StructuralNode;
import org.zaproxy.zap.network.DomainMatcher;
import org.zaproxy.zap.network.HttpRedirectionValidator;
import org.zaproxy.zap.network.HttpRequestConfig;
import org.zaproxy.zap.utils.ApiUtils;
import org.zaproxy.zap.utils.HarUtils;

public class CoreAPI extends ApiImplementor implements SessionListener {

    private static final Logger logger = LogManager.getLogger(CoreAPI.class);

    private enum ScanReportType {
        HTML,
        JSON,
        XML,
        MD
    }

    private static final String PREFIX = "core";
    private static final String ACTION_LOAD_SESSION = "loadSession";
    private static final String ACTION_NEW_SESSION = "newSession";
    private static final String ACTION_SAVE_SESSION = "saveSession";
    private static final String ACTION_SNAPSHOT_SESSION = "snapshotSession";

    private static final String ACTION_ACCESS_URL = "accessUrl";
    private static final String ACTION_SHUTDOWN = "shutdown";
    private static final String ACTION_EXCLUDE_FROM_PROXY = "excludeFromProxy";
    private static final String ACTION_CLEAR_EXCLUDED_FROM_PROXY = "clearExcludedFromProxy";
    private static final String ACTION_SET_HOME_DIRECTORY = "setHomeDirectory";
    private static final String ACTION_GENERATE_ROOT_CA = "generateRootCA";
    private static final String ACTION_SEND_REQUEST = "sendRequest";
    private static final String ACTION_DELETE_ALL_ALERTS = "deleteAllAlerts";
    private static final String ACTION_DELETE_ALERT = "deleteAlert";
    private static final String ACTION_COLLECT_GARBAGE = "runGarbageCollection";
    private static final String ACTION_SET_MODE = "setMode";
    private static final String ACTION_DELETE_SITE_NODE = "deleteSiteNode";
    private static final String ACTION_ADD_PROXY_CHAIN_EXCLUDED_DOMAIN =
            "addProxyChainExcludedDomain";
    private static final String ACTION_MODIFY_PROXY_CHAIN_EXCLUDED_DOMAIN =
            "modifyProxyChainExcludedDomain";
    private static final String ACTION_REMOVE_PROXY_CHAIN_EXCLUDED_DOMAIN =
            "removeProxyChainExcludedDomain";
    private static final String ACTION_ENABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS =
            "enableAllProxyChainExcludedDomains";
    private static final String ACTION_DISABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS =
            "disableAllProxyChainExcludedDomains";

    private static final String ACTION_OPTION_MAXIMUM_ALERT_INSTANCES =
            "setOptionMaximumAlertInstances";
    private static final String ACTION_OPTION_MERGE_RELATED_ALERTS = "setOptionMergeRelatedAlerts";
    private static final String ACTION_OPTION_ALERT_OVERRIDES_FILE_PATH =
            "setOptionAlertOverridesFilePath";
    private static final String ACTION_OPTION_USE_PROXY_CHAIN = "setOptionUseProxyChain";
    private static final String ACTION_ENABLE_PKCS12_CLIENT_CERTIFICATE =
            "enablePKCS12ClientCertificate";
    private static final String ACTION_DISABLE_CLIENT_CERTIFICATE = "disableClientCertificate";

    private static final String VIEW_ALERT = "alert";
    private static final String VIEW_ALERTS = "alerts";
    private static final String VIEW_ALERTS_SUMMARY = "alertsSummary";
    private static final String VIEW_NUMBER_OF_ALERTS = "numberOfAlerts";
    private static final String VIEW_HOSTS = "hosts";
    private static final String VIEW_SITES = "sites";
    private static final String VIEW_URLS = "urls";
    private static final String VIEW_CHILD_NODES = "childNodes";
    private static final String VIEW_MESSAGE = "message";
    private static final String VIEW_MESSAGES = "messages";
    private static final String VIEW_MESSAGES_BY_ID = "messagesById";
    private static final String VIEW_MODE = "mode";
    private static final String VIEW_NUMBER_OF_MESSAGES = "numberOfMessages";
    private static final String VIEW_VERSION = "version";
    private static final String VIEW_EXCLUDED_FROM_PROXY = "excludedFromProxy";
    private static final String VIEW_HOME_DIRECTORY = "homeDirectory";
    private static final String VIEW_SESSION_LOCATION = "sessionLocation";
    private static final String VIEW_PROXY_CHAIN_EXCLUDED_DOMAINS = "proxyChainExcludedDomains";
    private static final String VIEW_OPTION_PROXY_CHAIN_SKIP_NAME = "optionProxyChainSkipName";
    private static final String VIEW_OPTION_PROXY_EXCLUDED_DOMAINS = "optionProxyExcludedDomains";
    private static final String VIEW_OPTION_PROXY_EXCLUDED_DOMAINS_ENABLED =
            "optionProxyExcludedDomainsEnabled";
    private static final String VIEW_ZAP_HOME_PATH = "zapHomePath";

    private static final String VIEW_OPTION_MAXIMUM_ALERT_INSTANCES = "optionMaximumAlertInstances";
    private static final String VIEW_OPTION_MERGE_RELATED_ALERTS = "optionMergeRelatedAlerts";
    private static final String VIEW_OPTION_ALERT_OVERRIDES_FILE_PATH =
            "optionAlertOverridesFilePath";

    private static final String OTHER_PROXY_PAC = "proxy.pac";
    private static final String OTHER_SET_PROXY = "setproxy";
    private static final String OTHER_ROOT_CERT = "rootcert";
    private static final String OTHER_XML_REPORT = "xmlreport";
    private static final String OTHER_HTML_REPORT = "htmlreport";
    private static final String OTHER_JSON_REPORT = "jsonreport";
    private static final String OTHER_MD_REPORT = "mdreport";
    private static final String OTHER_MESSAGE_HAR = "messageHar";
    private static final String OTHER_MESSAGES_HAR = "messagesHar";
    private static final String OTHER_MESSAGES_HAR_BY_ID = "messagesHarById";
    private static final String OTHER_SEND_HAR_REQUEST = "sendHarRequest";
    private static final String OTHER_SCRIPT_JS = "script.js";

    private static final String PARAM_BASE_URL = "baseurl";
    private static final String PARAM_COUNT = "count";
    private static final String PARAM_DIR = "dir";
    private static final String PARAM_SESSION = "name";
    private static final String PARAM_OVERWRITE_SESSION = "overwrite";
    private static final String PARAM_REGEX = "regex";
    private static final String PARAM_START = "start";
    private static final String PARAM_PROXY_DETAILS = "proxy";
    private static final String PARAM_ID = "id";
    private static final String PARAM_IDS = "ids";
    private static final String PARAM_REQUEST = "request";
    private static final String PARAM_FOLLOW_REDIRECTS = "followRedirects";
    private static final String PARAM_MODE = "mode";
    private static final String PARAM_URL = "url";
    private static final String PARAM_METHOD = "method";
    private static final String PARAM_POST_DATA = "postData";
    private static final String PARAM_VALUE = "value";
    private static final String PARAM_IDX = "idx";
    private static final String PARAM_IS_REGEX = "isRegex";
    private static final String PARAM_IS_ENABLED = "isEnabled";
    private static final String PARAM_RISK = "riskId";
    private static final String PARAM_NUMBER_OF_INSTANCES = "numberOfInstances";
    private static final String PARAM_ENABLED = "enabled";
    private static final String PARAM_FILE_PATH = "filePath";
    private static final String PARAM_PASSWORD = "password";
    private static final String PARAM_INDEX = "index";

    /* Update the version whenever the script is changed (once per release) */
    protected static final int API_SCRIPT_VERSION = 2;
    private static final String API_SCRIPT =
            "function submitScript() {\n"
                    + "  var button=document.getElementById('button');\n"
                    + "  var component=button.getAttribute('zap-component')\n"
                    + "  var type=button.getAttribute('zap-type')\n"
                    + "  var name=button.getAttribute('zap-name')\n"
                    + "  var format\n"
                    + "  if (type == 'other') {\n"
                    + "    format = 'OTHER'\n"
                    + "  } else {\n"
                    + "    format = document.getElementById('zapapiformat').value\n"
                    + "  }\n"
                    + "  \n"
                    + "  var url = '/' + format + '/' + component + '/' + type + '/' + name + '/'\n"
                    + "  var form=document.getElementById('zapform');\n"
                    + "  form.action = url;\n"
                    + "  form.method = document.getElementById('formMethod').value;\n"
                    + "  form.submit();\n"
                    + "}\n"
                    + "document.addEventListener('DOMContentLoaded', function () {\n"
                    + "  var button=document.getElementById('button');\n"
                    + "  if (button) {\n"
                    + "    document.getElementById('button').addEventListener('click',  function(e) {submitScript();}, false);\n"
                    + "  }\n"
                    + "});\n";
    /* Allow caching for up to one day */
    private static final String API_SCRIPT_CACHE_CONTROL = "max-age=86400";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
    private boolean savingSession = false;
    private static ExtensionHistory extHistory;
    private ConnectionParam connectionParam;

    public CoreAPI(ConnectionParam connectionParam) {
        this.connectionParam = connectionParam;

        this.addApiAction(
                new ApiAction(
                        ACTION_ACCESS_URL,
                        new String[] {PARAM_URL},
                        new String[] {PARAM_FOLLOW_REDIRECTS}));
        this.addApiAction(new ApiAction(ACTION_SHUTDOWN));
        this.addApiAction(
                new ApiAction(
                        ACTION_NEW_SESSION,
                        null,
                        new String[] {PARAM_SESSION, PARAM_OVERWRITE_SESSION}));
        this.addApiAction(new ApiAction(ACTION_LOAD_SESSION, new String[] {PARAM_SESSION}));
        this.addApiAction(
                new ApiAction(
                        ACTION_SAVE_SESSION,
                        new String[] {PARAM_SESSION},
                        new String[] {PARAM_OVERWRITE_SESSION}));
        this.addApiAction(
                new ApiAction(
                        ACTION_SNAPSHOT_SESSION,
                        null,
                        new String[] {PARAM_SESSION, PARAM_OVERWRITE_SESSION}));
        this.addApiAction(new ApiAction(ACTION_CLEAR_EXCLUDED_FROM_PROXY));
        this.addApiAction(new ApiAction(ACTION_EXCLUDE_FROM_PROXY, new String[] {PARAM_REGEX}));
        this.addApiAction(new ApiAction(ACTION_SET_HOME_DIRECTORY, new String[] {PARAM_DIR}));
        this.addApiAction(new ApiAction(ACTION_SET_MODE, new String[] {PARAM_MODE}));
        this.addApiAction(new ApiAction(ACTION_GENERATE_ROOT_CA));
        this.addApiAction(
                new ApiAction(
                        ACTION_SEND_REQUEST,
                        new String[] {PARAM_REQUEST},
                        new String[] {PARAM_FOLLOW_REDIRECTS}));
        this.addApiAction(new ApiAction(ACTION_COLLECT_GARBAGE));
        this.addApiAction(
                new ApiAction(
                        ACTION_DELETE_SITE_NODE,
                        new String[] {PARAM_URL},
                        new String[] {PARAM_METHOD, PARAM_POST_DATA}));
        this.addApiAction(
                new ApiAction(
                        ACTION_ADD_PROXY_CHAIN_EXCLUDED_DOMAIN,
                        new String[] {PARAM_VALUE},
                        new String[] {PARAM_IS_REGEX, PARAM_IS_ENABLED}));
        this.addApiAction(
                new ApiAction(
                        ACTION_MODIFY_PROXY_CHAIN_EXCLUDED_DOMAIN,
                        new String[] {PARAM_IDX},
                        new String[] {PARAM_VALUE, PARAM_IS_REGEX, PARAM_IS_ENABLED}));
        this.addApiAction(
                new ApiAction(ACTION_REMOVE_PROXY_CHAIN_EXCLUDED_DOMAIN, new String[] {PARAM_IDX}));
        this.addApiAction(new ApiAction(ACTION_ENABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS));
        this.addApiAction(new ApiAction(ACTION_DISABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS));

        this.addApiAction(
                new ApiAction(
                        ACTION_OPTION_MAXIMUM_ALERT_INSTANCES,
                        new String[] {PARAM_NUMBER_OF_INSTANCES}));
        this.addApiAction(
                new ApiAction(ACTION_OPTION_MERGE_RELATED_ALERTS, new String[] {PARAM_ENABLED}));
        this.addApiAction(
                new ApiAction(
                        ACTION_OPTION_ALERT_OVERRIDES_FILE_PATH,
                        null,
                        new String[] {PARAM_FILE_PATH}));
        this.addApiAction(
                new ApiAction(
                        ACTION_ENABLE_PKCS12_CLIENT_CERTIFICATE,
                        new String[] {PARAM_FILE_PATH, PARAM_PASSWORD},
                        new String[] {PARAM_INDEX}));
        this.addApiAction(new ApiAction(ACTION_DISABLE_CLIENT_CERTIFICATE));

        // Deprecated actions
        this.addApiAction(depreciatedAlertApi(new ApiAction(ACTION_DELETE_ALL_ALERTS)));
        this.addApiAction(
                depreciatedAlertApi(new ApiAction(ACTION_DELETE_ALERT, new String[] {PARAM_ID})));

        this.addApiView(new ApiView(VIEW_HOSTS));
        this.addApiView(new ApiView(VIEW_SITES));
        this.addApiView(new ApiView(VIEW_URLS, null, new String[] {PARAM_BASE_URL}));
        this.addApiView(new ApiView(VIEW_CHILD_NODES, null, new String[] {PARAM_URL}));
        this.addApiView(new ApiView(VIEW_MESSAGE, new String[] {PARAM_ID}));
        this.addApiView(
                new ApiView(
                        VIEW_MESSAGES,
                        null,
                        new String[] {PARAM_BASE_URL, PARAM_START, PARAM_COUNT}));
        this.addApiView(new ApiView(VIEW_MESSAGES_BY_ID, new String[] {PARAM_IDS}));
        this.addApiView(new ApiView(VIEW_NUMBER_OF_MESSAGES, null, new String[] {PARAM_BASE_URL}));
        this.addApiView(new ApiView(VIEW_MODE));
        this.addApiView(new ApiView(VIEW_VERSION));
        this.addApiView(new ApiView(VIEW_EXCLUDED_FROM_PROXY));
        this.addApiView(new ApiView(VIEW_HOME_DIRECTORY));
        this.addApiView(new ApiView(VIEW_SESSION_LOCATION));
        this.addApiView(new ApiView(VIEW_PROXY_CHAIN_EXCLUDED_DOMAINS));
        ApiView apiView = new ApiView(VIEW_OPTION_PROXY_CHAIN_SKIP_NAME);
        apiView.setDeprecated(true);
        this.addApiView(apiView);
        apiView = new ApiView(VIEW_OPTION_PROXY_EXCLUDED_DOMAINS);
        apiView.setDeprecated(true);
        this.addApiView(apiView);
        apiView = new ApiView(VIEW_OPTION_PROXY_EXCLUDED_DOMAINS_ENABLED);
        apiView.setDeprecated(true);
        this.addApiView(apiView);
        this.addApiView(new ApiView(VIEW_ZAP_HOME_PATH));

        this.addApiView(new ApiView(VIEW_OPTION_MAXIMUM_ALERT_INSTANCES));
        this.addApiView(new ApiView(VIEW_OPTION_MERGE_RELATED_ALERTS));
        this.addApiView(new ApiView(VIEW_OPTION_ALERT_OVERRIDES_FILE_PATH));

        // Deprecated views
        this.addApiView(depreciatedAlertApi(new ApiView(VIEW_ALERT, new String[] {PARAM_ID})));
        this.addApiView(
                depreciatedAlertApi(
                        new ApiView(
                                VIEW_ALERTS,
                                null,
                                new String[] {
                                    PARAM_BASE_URL, PARAM_START, PARAM_COUNT, PARAM_RISK
                                })));
        this.addApiView(
                depreciatedAlertApi(
                        new ApiView(VIEW_ALERTS_SUMMARY, null, new String[] {PARAM_BASE_URL})));
        this.addApiView(
                (depreciatedAlertApi(
                        new ApiView(
                                VIEW_NUMBER_OF_ALERTS,
                                null,
                                new String[] {PARAM_BASE_URL, PARAM_RISK}))));

        this.addApiOthers(new ApiOther(OTHER_PROXY_PAC, false));
        this.addApiOthers(new ApiOther(OTHER_ROOT_CERT, false));
        this.addApiOthers(new ApiOther(OTHER_SET_PROXY, new String[] {PARAM_PROXY_DETAILS}));
        this.addApiOthers(new ApiOther(OTHER_XML_REPORT));
        this.addApiOthers(new ApiOther(OTHER_HTML_REPORT));
        this.addApiOthers(new ApiOther(OTHER_JSON_REPORT));
        this.addApiOthers(new ApiOther(OTHER_MD_REPORT));
        this.addApiOthers(new ApiOther(OTHER_MESSAGE_HAR, new String[] {PARAM_ID}));
        this.addApiOthers(
                new ApiOther(
                        OTHER_MESSAGES_HAR,
                        null,
                        new String[] {PARAM_BASE_URL, PARAM_START, PARAM_COUNT}));
        this.addApiOthers(new ApiOther(OTHER_MESSAGES_HAR_BY_ID, new String[] {PARAM_IDS}));
        this.addApiOthers(
                new ApiOther(
                        OTHER_SEND_HAR_REQUEST,
                        new String[] {PARAM_REQUEST},
                        new String[] {PARAM_FOLLOW_REDIRECTS}));

        this.addApiShortcut(OTHER_PROXY_PAC);
        // this.addApiShortcut(OTHER_ROOT_CERT);
        this.addApiShortcut(OTHER_SET_PROXY);
        this.addApiShortcut(OTHER_SCRIPT_JS);

        addApiOptions(this.connectionParam);
    }

    private <T extends ApiElement> T depreciatedAlertApi(T element) {
        element.setDeprecated(true);
        element.setDeprecatedDescription(Constant.messages.getString("core.api.depreciated.alert"));
        return element;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public ApiResponse handleApiAction(String name, JSONObject params) throws ApiException {

        Session session = Model.getSingleton().getSession();

        if (ACTION_ACCESS_URL.equals(name)) {
            URI uri;
            try {
                uri = new URI(params.getString(PARAM_URL), true);
            } catch (URIException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_URL, e);
            }
            HttpMessage request;
            try {
                request =
                        new HttpMessage(
                                new HttpRequestHeader(
                                        HttpRequestHeader.GET, uri, HttpHeader.HTTP11));
            } catch (HttpMalformedHeaderException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_URL, e);
            }
            return sendHttpMessage(request, getParam(params, PARAM_FOLLOW_REDIRECTS, false), name);
        } else if (ACTION_SHUTDOWN.equals(name)) {
            Thread thread =
                    new Thread("ZAP-Shutdown") {
                        @Override
                        public void run() {
                            try {
                                // Give the API a chance to return
                                sleep(1000);
                            } catch (InterruptedException e) {
                                // Ignore
                            }
                            try {
                                Control.getSingleton()
                                        .shutdown(
                                                Model.getSingleton()
                                                        .getOptionsParam()
                                                        .getDatabaseParam()
                                                        .isCompactDatabase());
                                logger.info(Constant.PROGRAM_TITLE + " terminated.");
                            } catch (Throwable e) {
                                logger.error("An error occurred while shutting down:", e);
                            } finally {
                                System.exit(0);
                            }
                        }
                    };
            thread.start();

        } else if (ACTION_SAVE_SESSION.equalsIgnoreCase(
                name)) { // Ignore case for backwards compatibility
            Path sessionPath = SessionUtils.getSessionPath(params.getString(PARAM_SESSION));
            String filename = sessionPath.toAbsolutePath().toString();

            final boolean overwrite = getParam(params, PARAM_OVERWRITE_SESSION, false);

            if (Files.exists(sessionPath)) {
                boolean sameSession = false;
                if (overwrite && !session.isNewState()) {
                    try {
                        sameSession =
                                Files.isSameFile(Paths.get(session.getFileName()), sessionPath);
                    } catch (IOException e) {
                        logger.error("Failed to check if same session path:", e);
                        throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                    }
                }

                if (!overwrite || sameSession) {
                    throw new ApiException(ApiException.Type.ALREADY_EXISTS, filename);
                }
            }
            this.savingSession = true;
            try {
                Control.getSingleton().saveSession(filename, this);
            } catch (Exception e) {
                logger.error("Failed to save the session:", e);
                this.savingSession = false;
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
            }
            // Wait for notification that its worked ok
            try {
                while (this.savingSession) {
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                // Probably not an error
                logger.debug(e.getMessage(), e);
            }
            logger.debug("Can now return after saving session");

        } else if (ACTION_SNAPSHOT_SESSION.equalsIgnoreCase(
                name)) { // Ignore case for backwards compatibility
            if (session.isNewState()) {
                throw new ApiException(ApiException.Type.DOES_NOT_EXIST);
            }

            List<String> actions = Control.getSingleton().getExtensionLoader().getActiveActions();
            if (!actions.isEmpty()) {
                throw new ApiException(
                        ApiException.Type.BAD_STATE,
                        "Active actions prevent the session snapshot: " + actions);
            }

            String fileName = ApiUtils.getOptionalStringParam(params, PARAM_SESSION);

            if (fileName == null || fileName.isEmpty()) {
                fileName = session.getFileName();

                if (fileName.endsWith(".session")) {
                    fileName = fileName.substring(0, fileName.length() - 8);
                }
                fileName += "-" + dateFormat.format(new Date()) + ".session";
            } else {
                Path sessionPath = SessionUtils.getSessionPath(fileName);
                fileName = sessionPath.toAbsolutePath().toString();

                if (Files.exists(sessionPath)) {
                    final boolean overwrite = getParam(params, PARAM_OVERWRITE_SESSION, false);
                    boolean sameSession = false;
                    try {
                        sameSession =
                                Files.isSameFile(Paths.get(session.getFileName()), sessionPath);
                    } catch (IOException e) {
                        logger.error("Failed to check if same session path:", e);
                        throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                    }

                    if (!overwrite || sameSession) {
                        throw new ApiException(ApiException.Type.ALREADY_EXISTS, fileName);
                    }
                }
            }

            this.savingSession = true;
            try {
                Control.getSingleton().snapshotSession(fileName, this);
            } catch (Exception e) {
                logger.error("Failed to snapshot the session:", e);
                this.savingSession = false;
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
            }
            // Wait for notification that its worked ok
            try {
                while (this.savingSession) {
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {
                // Probably not an error
                logger.debug(e.getMessage(), e);
            }
            logger.debug("Can now return after saving session");

        } else if (ACTION_LOAD_SESSION.equalsIgnoreCase(
                name)) { // Ignore case for backwards compatibility
            Path sessionPath = SessionUtils.getSessionPath(params.getString(PARAM_SESSION));
            String filename = sessionPath.toAbsolutePath().toString();

            if (!Files.exists(sessionPath)) {
                throw new ApiException(ApiException.Type.DOES_NOT_EXIST, filename);
            }
            try {
                Control.getSingleton().runCommandLineOpenSession(filename);
            } catch (Exception e) {
                logger.error("Failed to load the session:", e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
            }

        } else if (ACTION_NEW_SESSION.equalsIgnoreCase(
                name)) { // Ignore case for backwards compatibility
            String sessionName = null;
            try {
                sessionName = params.getString(PARAM_SESSION);
            } catch (Exception e1) {
                // Ignore
            }
            if (sessionName == null || sessionName.length() == 0) {
                // Create a new 'unnamed' session
                Control.getSingleton().discardSession();
                try {
                    Control.getSingleton().newSession();
                } catch (Exception e) {
                    logger.error("Failed to create a new session:", e);
                    throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                }
            } else {
                Path sessionPath = SessionUtils.getSessionPath(sessionName);
                String filename = sessionPath.toAbsolutePath().toString();

                final boolean overwrite = getParam(params, PARAM_OVERWRITE_SESSION, false);

                if (Files.exists(sessionPath) && !overwrite) {
                    throw new ApiException(ApiException.Type.ALREADY_EXISTS, filename);
                }
                try {
                    Control.getSingleton().runCommandLineNewSession(filename);
                } catch (Exception e) {
                    logger.error("Failed to create a new session:", e);
                    throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                }
            }
        } else if (ACTION_CLEAR_EXCLUDED_FROM_PROXY.equals(name)) {
            try {
                session.setExcludeFromProxyRegexs(new ArrayList<String>());
            } catch (DatabaseException e) {
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
            }
        } else if (ACTION_EXCLUDE_FROM_PROXY.equals(name)) {
            String regex = params.getString(PARAM_REGEX);
            try {
                session.addExcludeFromProxyRegex(regex);
            } catch (DatabaseException e) {
                logger.error(e.getMessage(), e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
            } catch (PatternSyntaxException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_REGEX);
            }
        } else if (ACTION_SET_HOME_DIRECTORY.equals(name)) {
            File f = new File(params.getString(PARAM_DIR));
            if (f.exists() && f.isDirectory()) {
                Model.getSingleton().getOptionsParam().setUserDirectory(f);
            } else {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_DIR);
            }
        } else if (ACTION_SET_MODE.equals(name)) {
            try {
                Mode mode = Mode.valueOf(params.getString(PARAM_MODE).toLowerCase());
                if (View.isInitialised()) {
                    View.getSingleton().getMainFrame().getMainToolbarPanel().setMode(mode);
                    View.getSingleton().getMainFrame().getMainMenuBar().setMode(mode);
                } else {
                    Control.getSingleton().setMode(mode);
                }
            } catch (Exception e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_MODE);
            }
        } else if (ACTION_GENERATE_ROOT_CA.equals(name)) {
            ExtensionDynSSL extDyn =
                    Control.getSingleton().getExtensionLoader().getExtension(ExtensionDynSSL.class);
            if (extDyn != null) {
                try {
                    extDyn.createNewRootCa();
                } catch (Exception e) {
                    logger.error("Failed to create the new Root CA cert:", e);
                    throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                }
            }
        } else if (ACTION_SEND_REQUEST.equals(name)) {
            HttpMessage request;
            try {
                request = createRequest(params.getString(PARAM_REQUEST));
            } catch (HttpMalformedHeaderException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_REQUEST, e);
            }
            validateForCurrentMode(request);
            return sendHttpMessage(request, getParam(params, PARAM_FOLLOW_REDIRECTS, false), name);
        } else if (ACTION_DELETE_ALL_ALERTS.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiAction(name, params);
        } else if (ACTION_DELETE_ALERT.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiAction(name, params);
        } else if (ACTION_COLLECT_GARBAGE.equals(name)) {
            System.gc();
            return ApiResponseElement.OK;

        } else if (ACTION_DELETE_SITE_NODE.equals(name)) {
            try {
                String url = params.getString(PARAM_URL);
                String method = getParam(params, PARAM_METHOD, "GET");
                String postData = getParam(params, PARAM_POST_DATA, "");
                URI uri = new URI(url, true);
                SiteMap siteMap = session.getSiteTree();
                SiteNode siteNode = siteMap.findNode(uri, method, postData);
                if (siteNode == null) {
                    throw new ApiException(ApiException.Type.DOES_NOT_EXIST, PARAM_URL);
                }
                if (getExtHistory() != null) {
                    getExtHistory().purge(siteMap, siteNode);
                }
                return ApiResponseElement.OK;
            } catch (URIException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_URL, e);
            }
        } else if (ACTION_ADD_PROXY_CHAIN_EXCLUDED_DOMAIN.equals(name)) {
            try {
                ConnectionParam connectionParam =
                        Model.getSingleton().getOptionsParam().getConnectionParam();
                String value = params.getString(PARAM_VALUE);
                DomainMatcher domain;
                if (getParam(params, PARAM_IS_REGEX, false)) {
                    domain = new DomainMatcher(DomainMatcher.createPattern(value));
                } else {
                    domain = new DomainMatcher(value);
                }
                domain.setEnabled(getParam(params, PARAM_IS_ENABLED, true));

                List<DomainMatcher> domains =
                        new ArrayList<>(connectionParam.getProxyExcludedDomains());
                domains.add(domain);
                connectionParam.setProxyExcludedDomains(domains);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_VALUE, e);
            }
        } else if (ACTION_MODIFY_PROXY_CHAIN_EXCLUDED_DOMAIN.equals(name)) {
            try {
                ConnectionParam connectionParam =
                        Model.getSingleton().getOptionsParam().getConnectionParam();
                int idx = params.getInt(PARAM_IDX);
                if (idx < 0 || idx >= connectionParam.getProxyExcludedDomains().size()) {
                    throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_IDX);
                }

                DomainMatcher oldDomain = connectionParam.getProxyExcludedDomains().get(idx);
                String value = getParam(params, PARAM_VALUE, oldDomain.getValue());
                if (value.isEmpty()) {
                    value = oldDomain.getValue();
                }

                DomainMatcher newDomain;
                if (getParam(params, PARAM_IS_REGEX, oldDomain.isRegex())) {
                    newDomain = new DomainMatcher(DomainMatcher.createPattern(value));
                } else {
                    newDomain = new DomainMatcher(value);
                }
                newDomain.setEnabled(getParam(params, PARAM_IS_ENABLED, oldDomain.isEnabled()));

                if (!oldDomain.equals(newDomain)) {
                    List<DomainMatcher> domains =
                            new ArrayList<>(connectionParam.getProxyExcludedDomains());
                    domains.set(idx, newDomain);
                    connectionParam.setProxyExcludedDomains(domains);
                }
            } catch (JSONException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_IDX, e);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_VALUE, e);
            }
        } else if (ACTION_REMOVE_PROXY_CHAIN_EXCLUDED_DOMAIN.equals(name)) {
            try {
                ConnectionParam connectionParam =
                        Model.getSingleton().getOptionsParam().getConnectionParam();
                int idx = params.getInt(PARAM_IDX);
                if (idx < 0 || idx >= connectionParam.getProxyExcludedDomains().size()) {
                    throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_IDX);
                }

                List<DomainMatcher> domains =
                        new ArrayList<>(connectionParam.getProxyExcludedDomains());
                domains.remove(idx);
                connectionParam.setProxyExcludedDomains(domains);
            } catch (JSONException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_IDX, e);
            }
        } else if (ACTION_ENABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS.equals(name)) {
            setProxyChainExcludedDomainsEnabled(true);
        } else if (ACTION_DISABLE_ALL_PROXY_CHAIN_EXCLUDED_DOMAINS.equals(name)) {
            setProxyChainExcludedDomainsEnabled(false);
        } else if (ACTION_OPTION_MAXIMUM_ALERT_INSTANCES.equals(name)) {
            try {
                getAlertParam(ApiException.Type.BAD_ACTION)
                        .setMaximumInstances(params.getInt(PARAM_NUMBER_OF_INSTANCES));
            } catch (JSONException e) {
                throw new ApiException(
                        ApiException.Type.ILLEGAL_PARAMETER, PARAM_NUMBER_OF_INSTANCES, e);
            }
        } else if (ACTION_OPTION_MERGE_RELATED_ALERTS.equals(name)) {
            try {
                getAlertParam(ApiException.Type.BAD_ACTION)
                        .setMergeRelatedIssues(params.getBoolean(PARAM_ENABLED));
            } catch (JSONException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_ENABLED, e);
            }
        } else if (ACTION_OPTION_ALERT_OVERRIDES_FILE_PATH.equals(name)) {
            String filePath = getParam(params, PARAM_FILE_PATH, "");
            if (!filePath.isEmpty()) {
                File file = new File(filePath);
                if (!file.isFile() || !file.canRead()) {
                    throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_FILE_PATH);
                }
            }
            getAlertParam(ApiException.Type.BAD_ACTION).setOverridesFilename(filePath);
            if (!Control.getSingleton()
                    .getExtensionLoader()
                    .getExtension(ExtensionAlert.class)
                    .reloadOverridesFile()) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_FILE_PATH);
            }
        } else if (ACTION_ENABLE_PKCS12_CLIENT_CERTIFICATE.equals(name)) {
            String filePath = getParam(params, PARAM_FILE_PATH, "");
            if (!filePath.isEmpty()) {
                File file = new File(filePath);
                if (!file.isFile() || !file.canRead()) {
                    throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_FILE_PATH);
                }
            }
            String password = getParam(params, PARAM_PASSWORD, "");
            if (password.isEmpty()) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_PASSWORD);
            }
            int certIndex = getParam(params, PARAM_INDEX, 0);
            if (certIndex < 0) {
                certIndex = 0;
            }

            OptionsParamCertificate certParams =
                    Model.getSingleton().getOptionsParam().getCertificateParam();
            try {
                SSLContextManager contextManager = certParams.getSSLContextManager();
                int ksIndex = contextManager.loadPKCS12Certificate(filePath, password);
                contextManager.unlockKey(ksIndex, certIndex, password);
                contextManager.setDefaultKey(ksIndex, certIndex);
                certParams.setActiveCertificate();
                certParams.setEnableCertificate(true);
                logger.info("Client Certificate enabled from API");
            } catch (IOException
                    | CertificateException
                    | NoSuchAlgorithmException
                    | KeyStoreException
                    | KeyManagementException ex) {
                logger.error("The certificate could not be enabled due to an error", ex);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR, ex);
            }
            return ApiResponseElement.OK;

        } else if (ACTION_DISABLE_CLIENT_CERTIFICATE.equals(name)) {
            Model.getSingleton()
                    .getOptionsParam()
                    .getCertificateParam()
                    .setEnableCertificate(false);
            logger.info("Client Certificate disabled from API");

            return ApiResponseElement.OK;
        } else {
            throw new ApiException(ApiException.Type.BAD_ACTION);
        }
        return ApiResponseElement.OK;
    }

    @Override
    public ApiResponse handleApiOptionAction(String name, JSONObject params) throws ApiException {
        if (ACTION_OPTION_USE_PROXY_CHAIN.equals(name)) {
            boolean enabled = params.getBoolean("Boolean");
            if (enabled
                    && (connectionParam.getProxyChainName() == null
                            || connectionParam.getProxyChainName().isEmpty())) {
                return ApiResponseElement.FAIL;
            }

            connectionParam.setUseProxyChain(enabled);
            return ApiResponseElement.OK;
        }
        return super.handleApiOptionAction(name, params);
    }

    private void setProxyChainExcludedDomainsEnabled(boolean enabled) {
        ConnectionParam connectionParam =
                Model.getSingleton().getOptionsParam().getConnectionParam();
        List<DomainMatcher> domains = connectionParam.getProxyExcludedDomains();
        for (DomainMatcher domain : domains) {
            domain.setEnabled(enabled);
        }
        connectionParam.setProxyExcludedDomains(domains);
    }

    private AlertParam getAlertParam(ApiException.Type type) throws ApiException {
        AlertParam alertOptions =
                Model.getSingleton().getOptionsParam().getParamSet(AlertParam.class);
        if (alertOptions == null) {
            throw new ApiException(type);
        }
        return alertOptions;
    }

    /**
     * Validates that the given request is valid for the current {@link Mode}.
     *
     * @param request the request that will be validated
     * @throws ApiException if the request is not valid for the current {@code Mode}.
     * @see #isValidForCurrentMode(URI)
     */
    private static void validateForCurrentMode(HttpMessage request) throws ApiException {
        if (!isValidForCurrentMode(request.getRequestHeader().getURI())) {
            throw new ApiException(ApiException.Type.MODE_VIOLATION);
        }
    }

    /**
     * Tells whether or not the given {@code uri} is valid for the current {@link Mode}.
     *
     * <p>The {@code uri} is not valid if the mode is {@code safe} or if in {@code protect} mode is
     * not in scope.
     *
     * @param uri the {@code URI} that will be validated
     * @return {@code true} if the given {@code uri} is valid, {@code false} otherwise.
     */
    private static boolean isValidForCurrentMode(URI uri) {
        switch (Control.getSingleton().getMode()) {
            case safe:
                return false;
            case protect:
                return Model.getSingleton().getSession().isInScope(uri.toString());
            default:
                return true;
        }
    }

    private ApiResponse sendHttpMessage(
            HttpMessage request, boolean followRedirects, String apiResponseName)
            throws ApiException {
        final ApiResponseList resultList = new ApiResponseList(apiResponseName);
        try {
            sendRequest(
                    request,
                    followRedirects,
                    new Processor<HttpMessage>() {

                        @Override
                        public void process(HttpMessage msg) {
                            int id = -1;
                            int type = -1;
                            HistoryReference hRef = msg.getHistoryRef();
                            if (hRef != null) {
                                id = hRef.getHistoryId();
                                type = hRef.getHistoryType();
                            }
                            resultList.addItem(
                                    ApiResponseConversionUtils.httpMessageToSet(id, type, msg));
                        }
                    });

            return resultList;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Failed to send the HTTP request:", e);
            throw new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
        }
    }

    private static ExtensionHistory getExtHistory() {
        if (extHistory == null) {
            extHistory =
                    Control.getSingleton()
                            .getExtensionLoader()
                            .getExtension(ExtensionHistory.class);
        }
        return extHistory;
    }

    private static HttpMessage createRequest(String request) throws HttpMalformedHeaderException {
        HttpMessage requestMsg = new HttpMessage();
        String[] parts = request.split(Pattern.quote(HttpHeader.CRLF + HttpHeader.CRLF), 2);

        requestMsg.setRequestHeader(parts[0]);

        if (parts.length > 1) {
            requestMsg.setRequestBody(parts[1]);
        } else {
            requestMsg.setRequestBody("");
        }
        return requestMsg;
    }

    private static void sendRequest(
            HttpMessage request, boolean followRedirects, Processor<HttpMessage> processor)
            throws IOException, ApiException {
        HttpSender sender = null;
        try {
            sender = createHttpSender();

            if (followRedirects) {
                ModeRedirectionValidator redirector = new ModeRedirectionValidator(processor);
                sender.sendAndReceive(
                        request,
                        HttpRequestConfig.builder().setRedirectionValidator(redirector).build());

                if (!redirector.isRequestValid()) {
                    throw new ApiException(ApiException.Type.MODE_VIOLATION);
                }
            } else {
                sender.sendAndReceive(request, false);
                persistMessage(request);
                processor.process(request);
            }
        } finally {
            if (sender != null) {
                sender.shutdown();
            }
        }
    }

    private static HttpSender createHttpSender() {
        return new HttpSender(
                Model.getSingleton().getOptionsParam().getConnectionParam(),
                true,
                HttpSender.MANUAL_REQUEST_INITIATOR);
    }

    private static void persistMessage(final HttpMessage message) {
        final HistoryReference historyRef;

        try {
            historyRef =
                    new HistoryReference(
                            Model.getSingleton().getSession(),
                            HistoryReference.TYPE_ZAP_USER,
                            message);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            return;
        }

        if (getExtHistory() != null) {
            EventQueue.invokeLater(
                    new Runnable() {

                        @Override
                        public void run() {
                            getExtHistory().addHistory(historyRef);
                            Model.getSingleton()
                                    .getSession()
                                    .getSiteTree()
                                    .addPath(historyRef, message);
                        }
                    });
        }
    }

    @Override
    public ApiResponse handleApiView(String name, JSONObject params) throws ApiException {
        ApiResponse result = null;
        Model model = Model.getSingleton();
        Session session = model.getSession();

        if (VIEW_HOSTS.equals(name)) {
            result = new ApiResponseList(name);
            SiteNode root = session.getSiteTree().getRoot();
            @SuppressWarnings("unchecked")
            Enumeration<TreeNode> en = root.children();
            while (en.hasMoreElements()) {
                String site = ((SiteNode) en.nextElement()).getNodeName();
                if (site.indexOf("//") >= 0) {
                    site = site.substring(site.indexOf("//") + 2);
                }
                if (site.indexOf(":") >= 0) {
                    site = site.substring(0, site.indexOf(":"));
                }
                ((ApiResponseList) result).addItem(new ApiResponseElement("host", site));
            }
        } else if (VIEW_SITES.equals(name)) {
            ApiResponseList sitesList = new ApiResponseList(name);
            StructuralNode root = SessionStructure.getRootNode(model);
            if (root != null) {
                for (Iterator<StructuralNode> it = root.getChildIterator(); it.hasNext(); ) {
                    sitesList.addItem(new ApiResponseElement("site", it.next().getName()));
                }
            }
            result = sitesList;
        } else if (VIEW_URLS.equals(name)) {
            result = new ApiResponseList(name);
            SiteNode root = session.getSiteTree().getRoot();
            addUrlsToList(
                    getParam(params, PARAM_BASE_URL, ""),
                    root,
                    new HashSet<String>(),
                    (ApiResponseList) result);
        } else if (VIEW_CHILD_NODES.equals(name)) {
            StructuralNode node;
            String url = this.getParam(params, PARAM_URL, "");

            if (url.trim().length() == 0) {
                node = SessionStructure.getRootNode(model);
            } else {
                try {
                    node =
                            SessionStructure.find(
                                    Model.getSingleton(), new URI(url, false), null, null);
                } catch (URIException e) {
                    throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_URL, e);
                } catch (DatabaseException e) {
                    throw new ApiException(ApiException.Type.INTERNAL_ERROR, e);
                }
            }
            if (node == null) {
                throw new ApiException(ApiException.Type.DOES_NOT_EXIST, PARAM_URL);
            }
            result = new ApiResponseList(name);

            Iterator<StructuralNode> iter = node.getChildIterator();
            while (iter.hasNext()) {
                ((ApiResponseList) result).addItem(structuralNodeToResponse(iter.next()));
            }
        } else if (VIEW_ALERT.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiView(name, params);
        } else if (VIEW_ALERTS.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiView(name, params);
        } else if (VIEW_NUMBER_OF_ALERTS.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiView(name, params);
        } else if (VIEW_ALERTS_SUMMARY.equals(name)) {
            return API.getInstance()
                    .getImplementors()
                    .get(AlertAPI.PREFIX)
                    .handleApiView(name, params);
        } else if (VIEW_MESSAGE.equals(name)) {
            TableHistory tableHistory = Model.getSingleton().getDb().getTableHistory();
            RecordHistory recordHistory =
                    getRecordHistory(tableHistory, getParam(params, PARAM_ID, -1));
            result =
                    new ApiResponseElement(
                            ApiResponseConversionUtils.httpMessageToSet(
                                    recordHistory.getHistoryId(),
                                    recordHistory.getHistoryType(),
                                    recordHistory.getHttpMessage()));
        } else if (VIEW_MESSAGES.equals(name)) {
            final ApiResponseList resultList = new ApiResponseList(name);
            processHttpMessages(
                    this.getParam(params, PARAM_BASE_URL, (String) null),
                    this.getParam(params, PARAM_START, -1),
                    this.getParam(params, PARAM_COUNT, -1),
                    new Processor<RecordHistory>() {

                        @Override
                        public void process(RecordHistory recordHistory) {
                            resultList.addItem(
                                    ApiResponseConversionUtils.httpMessageToSet(
                                            recordHistory.getHistoryId(),
                                            recordHistory.getHistoryType(),
                                            recordHistory.getHttpMessage()));
                        }
                    });
            result = resultList;
        } else if (VIEW_NUMBER_OF_MESSAGES.equals(name)) {
            CounterProcessor<RecordHistory> counter = new CounterProcessor<>();
            processHttpMessages(
                    this.getParam(params, PARAM_BASE_URL, (String) null),
                    this.getParam(params, PARAM_START, -1),
                    this.getParam(params, PARAM_COUNT, -1),
                    counter);

            result = new ApiResponseElement(name, Integer.toString(counter.getCount()));
        } else if (VIEW_MESSAGES_BY_ID.equals(name)) {
            ApiResponseList resultList = new ApiResponseList(name);
            TableHistory tableHistory = Model.getSingleton().getDb().getTableHistory();
            for (Integer id : getIds(params)) {
                RecordHistory recordHistory = getRecordHistory(tableHistory, id);
                resultList.addItem(
                        ApiResponseConversionUtils.httpMessageToSet(
                                recordHistory.getHistoryId(),
                                recordHistory.getHistoryType(),
                                recordHistory.getHttpMessage()));
            }
            result = resultList;
        } else if (VIEW_MODE.equals(name)) {
            result = new ApiResponseElement(name, Control.getSingleton().getMode().name());
        } else if (VIEW_VERSION.equals(name)) {
            result = new ApiResponseElement(name, Constant.PROGRAM_VERSION);
        } else if (VIEW_EXCLUDED_FROM_PROXY.equals(name)) {
            result = new ApiResponseList(name);
            List<String> regexs = session.getExcludeFromProxyRegexs();
            for (String regex : regexs) {
                ((ApiResponseList) result).addItem(new ApiResponseElement("regex", regex));
            }
        } else if (VIEW_HOME_DIRECTORY.equals(name)) {
            result =
                    new ApiResponseElement(
                            name,
                            Model.getSingleton()
                                    .getOptionsParam()
                                    .getUserDirectory()
                                    .getAbsolutePath());

        } else if (VIEW_SESSION_LOCATION.equals(name)) {
            result = new ApiResponseElement(name, session.getFileName());
        } else if (VIEW_PROXY_CHAIN_EXCLUDED_DOMAINS.equals(name)
                || VIEW_OPTION_PROXY_EXCLUDED_DOMAINS.equals(name)
                || VIEW_OPTION_PROXY_CHAIN_SKIP_NAME.equals(name)) {
            result =
                    proxyChainExcludedDomainsToApiResponseList(
                            name,
                            Model.getSingleton()
                                    .getOptionsParam()
                                    .getConnectionParam()
                                    .getProxyExcludedDomains(),
                            false);
        } else if (VIEW_OPTION_PROXY_EXCLUDED_DOMAINS_ENABLED.equals(name)) {
            result =
                    proxyChainExcludedDomainsToApiResponseList(
                            name,
                            Model.getSingleton()
                                    .getOptionsParam()
                                    .getConnectionParam()
                                    .getProxyExcludedDomains(),
                            true);
        } else if (VIEW_ZAP_HOME_PATH.equals(name)) {
            result = new ApiResponseElement(name, Constant.getZapHome());
        } else if (VIEW_OPTION_MAXIMUM_ALERT_INSTANCES.equals(name)) {
            result =
                    new ApiResponseElement(
                            name,
                            String.valueOf(
                                    getAlertParam(ApiException.Type.BAD_VIEW)
                                            .getMaximumInstances()));
        } else if (VIEW_OPTION_MERGE_RELATED_ALERTS.equals(name)) {
            result =
                    new ApiResponseElement(
                            name,
                            String.valueOf(
                                    getAlertParam(ApiException.Type.BAD_VIEW)
                                            .isMergeRelatedIssues()));
        } else if (VIEW_OPTION_ALERT_OVERRIDES_FILE_PATH.equals(name)) {
            result =
                    new ApiResponseElement(
                            name, getAlertParam(ApiException.Type.BAD_VIEW).getOverridesFilename());
        } else {
            throw new ApiException(ApiException.Type.BAD_VIEW);
        }
        return result;
    }

    private RecordHistory getRecordHistory(TableHistory tableHistory, Integer id)
            throws ApiException {
        RecordHistory recordHistory;
        try {
            recordHistory = tableHistory.read(id);
        } catch (HttpMalformedHeaderException | DatabaseException e) {
            logger.error("Failed to read the history record:", e);
            throw new ApiException(ApiException.Type.INTERNAL_ERROR, e);
        }
        if (recordHistory == null) {
            throw new ApiException(ApiException.Type.DOES_NOT_EXIST, Integer.toString(id));
        }
        return recordHistory;
    }

    private ApiResponseSet<Object> structuralNodeToResponse(StructuralNode node) {
        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("name", node.getName());
        nodeData.put("method", node.getMethod());
        nodeData.put("uri", node.getURI().toString());
        nodeData.put("isLeaf", node.isLeaf());
        nodeData.put("hrefId", node.getHistoryReference().getHistoryId());
        return new ApiResponseSet<Object>("node", nodeData);
    }

    private ApiResponse proxyChainExcludedDomainsToApiResponseList(
            String name, List<DomainMatcher> domains, boolean excludeDisabled) {
        ApiResponseList apiResponse = new ApiResponseList(name);
        for (int i = 0; i < domains.size(); i++) {
            DomainMatcher domain = domains.get(i);
            if (!domain.isEnabled() && excludeDisabled) {
                continue;
            }
            Map<String, Object> domainData = new HashMap<>();
            domainData.put("idx", i);
            domainData.put("value", domain.getValue());
            domainData.put("regex", domain.isRegex());
            domainData.put("enabled", domain.isEnabled());
            apiResponse.addItem(new ApiResponseSet<Object>("domain", domainData));
        }
        return apiResponse;
    }

    @Override
    public HttpMessage handleApiOther(HttpMessage msg, String name, JSONObject params)
            throws ApiException {

        if (OTHER_PROXY_PAC.equals(name)) {
            final ProxyParam proxyParam = Model.getSingleton().getOptionsParam().getProxyParam();
            final int port = proxyParam.getProxyPort();
            try {
                String domain = null;
                if (proxyParam.isProxyIpAnyLocalAddress()) {
                    String localDomain = msg.getRequestHeader().getHostName();
                    if (!API.API_DOMAIN.equals(localDomain)) {
                        domain = localDomain;
                    }
                }
                if (domain == null) {
                    domain = proxyParam.getProxyIp();
                }
                String response = this.getPacFile(domain, port);
                msg.setResponseHeader(API.getDefaultResponseHeader("text/html", response.length()));

                msg.setResponseBody(response);

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
            return msg;
        } else if (OTHER_SET_PROXY.equals(name)) {
            /* JSON string:
             *  {"type":1,
             *  "http":	{"host":"proxy.corp.com","port":80},
             *  "ssl":	{"host":"proxy.corp.com","port":80},
             *  "ftp":{"host":"proxy.corp.com","port":80},
             *  "socks":{"host":"proxy.corp.com","port":80},
             *  "shareSettings":true,"socksVersion":5,
             *  "proxyExcludes":"localhost, 127.0.0.1"}
             */
            String proxyDetails = params.getString(PARAM_PROXY_DETAILS);
            String response = "OK";

            try {
                try {
                    JSONObject json = JSONObject.fromObject(proxyDetails);

                    if (json.getInt("type") == 1) {
                        JSONObject httpJson = JSONObject.fromObject(json.get("http"));
                        String proxyHost = httpJson.getString("host");
                        int proxyPort = httpJson.getInt("port");

                        if (proxyHost != null && proxyHost.length() > 0 && proxyPort > 0) {
                            Model.getSingleton()
                                    .getOptionsParam()
                                    .getConnectionParam()
                                    .setProxyChainName(proxyHost);
                            Model.getSingleton()
                                    .getOptionsParam()
                                    .getConnectionParam()
                                    .setProxyChainPort(proxyPort);
                        }
                    }
                } catch (JSONException e) {
                    throw new ApiException(
                            ApiException.Type.ILLEGAL_PARAMETER, PARAM_PROXY_DETAILS);
                }
                msg.setResponseHeader(API.getDefaultResponseHeader("text/html", response.length()));

                msg.setResponseBody(response);

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }

            return msg;
        } else if (OTHER_ROOT_CERT.equals(name)) {
            ExtensionDynSSL extDynSSL =
                    Control.getSingleton().getExtensionLoader().getExtension(ExtensionDynSSL.class);
            if (extDynSSL != null) {
                try {
                    Certificate rootCA = extDynSSL.getRootCA();
                    if (rootCA == null) {
                        throw new ApiException(ApiException.Type.DOES_NOT_EXIST);
                    }
                    final StringWriter sw = new StringWriter();
                    try (final PemWriter pw = new PemWriter(sw)) {
                        pw.writeObject(new JcaMiscPEMGenerator(rootCA));
                        pw.flush();
                    }
                    String response = sw.toString();
                    msg.setResponseHeader(
                            API.getDefaultResponseHeader(
                                    "application/pkix-cert;", response.length()));

                    msg.setResponseBody(response);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    throw new ApiException(ApiException.Type.INTERNAL_ERROR);
                }

            } else {
                throw new ApiException(ApiException.Type.DOES_NOT_EXIST);
            }

            return msg;
        } else if (OTHER_XML_REPORT.equals(name)) {
            try {
                writeReportLastScanTo(msg, ScanReportType.XML);

                return msg;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR);
            }
        } else if (OTHER_HTML_REPORT.equals(name)) {
            try {
                writeReportLastScanTo(msg, ScanReportType.HTML);

                return msg;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR);
            }
        } else if (OTHER_JSON_REPORT.equals(name)) {
            try {
                writeReportLastScanTo(msg, ScanReportType.JSON);

                return msg;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR);
            }
        } else if (OTHER_MD_REPORT.equals(name)) {
            try {
                writeReportLastScanTo(msg, ScanReportType.MD);

                return msg;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new ApiException(ApiException.Type.INTERNAL_ERROR);
            }
        } else if (OTHER_MESSAGE_HAR.equals(name)) {
            byte[] responseBody;
            try {
                final HarEntries entries = new HarEntries();
                TableHistory tableHistory = Model.getSingleton().getDb().getTableHistory();
                RecordHistory recordHistory =
                        getRecordHistory(tableHistory, getParam(params, PARAM_ID, -1));
                addHarEntry(entries, recordHistory);

                HarLog harLog = HarUtils.createZapHarLog();
                harLog.setEntries(entries);

                responseBody = HarUtils.harLogToByteArray(harLog);
            } catch (ApiException e) {
                responseBody =
                        e.toString(API.Format.JSON, incErrorDetails())
                                .getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);

                ApiException apiException =
                        new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                responseBody =
                        apiException
                                .toString(API.Format.JSON, incErrorDetails())
                                .getBytes(StandardCharsets.UTF_8);
            }

            try {
                msg.setResponseHeader(
                        API.getDefaultResponseHeader(
                                "application/json; charset=UTF-8", responseBody.length));
            } catch (HttpMalformedHeaderException e) {
                logger.error("Failed to create response header: " + e.getMessage(), e);
            }
            msg.setResponseBody(responseBody);

            return msg;
        } else if (OTHER_MESSAGES_HAR_BY_ID.equals(name) || OTHER_MESSAGES_HAR.equals(name)) {
            byte[] responseBody;
            try {
                final HarEntries entries = new HarEntries();
                if (OTHER_MESSAGES_HAR_BY_ID.equals(name)) {
                    TableHistory tableHistory = Model.getSingleton().getDb().getTableHistory();
                    for (Integer id : getIds(params)) {
                        RecordHistory recordHistory = getRecordHistory(tableHistory, id);
                        addHarEntry(entries, recordHistory);
                    }
                } else {
                    processHttpMessages(
                            this.getParam(params, PARAM_BASE_URL, (String) null),
                            this.getParam(params, PARAM_START, -1),
                            this.getParam(params, PARAM_COUNT, -1),
                            rh -> addHarEntry(entries, rh));
                }

                HarLog harLog = HarUtils.createZapHarLog();
                harLog.setEntries(entries);

                responseBody = HarUtils.harLogToByteArray(harLog);
            } catch (ApiException e) {
                responseBody =
                        e.toString(API.Format.JSON, incErrorDetails())
                                .getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);

                ApiException apiException =
                        new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                responseBody =
                        apiException
                                .toString(API.Format.JSON, incErrorDetails())
                                .getBytes(StandardCharsets.UTF_8);
            }

            try {
                msg.setResponseHeader(
                        API.getDefaultResponseHeader(
                                "application/json; charset=UTF-8", responseBody.length));
            } catch (HttpMalformedHeaderException e) {
                logger.error("Failed to create response header: " + e.getMessage(), e);
            }
            msg.setResponseBody(responseBody);

            return msg;
        } else if (OTHER_SEND_HAR_REQUEST.equals(name)) {
            byte[] responseBody = {};
            HttpMessage request = null;
            try {
                request = HarUtils.createHttpMessage(params.getString(PARAM_REQUEST));
            } catch (IOException e) {
                ApiException apiException =
                        new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_REQUEST, e);
                responseBody =
                        apiException
                                .toString(API.Format.JSON, incErrorDetails())
                                .getBytes(StandardCharsets.UTF_8);
            }

            if (request != null) {
                if (!isValidForCurrentMode(request.getRequestHeader().getURI())) {
                    ApiException apiException = new ApiException(ApiException.Type.MODE_VIOLATION);
                    responseBody =
                            apiException
                                    .toString(API.Format.JSON, incErrorDetails())
                                    .getBytes(StandardCharsets.UTF_8);
                } else {
                    boolean followRedirects = getParam(params, PARAM_FOLLOW_REDIRECTS, false);
                    try {
                        final HarEntries entries = new HarEntries();
                        sendRequest(
                                request,
                                followRedirects,
                                httpMessage -> {
                                    HistoryReference hRef = httpMessage.getHistoryRef();
                                    entries.addEntry(
                                            HarUtils.createHarEntry(
                                                    hRef.getHistoryId(),
                                                    hRef.getHistoryType(),
                                                    httpMessage));
                                });

                        HarLog harLog = HarUtils.createZapHarLog();
                        harLog.setEntries(entries);

                        responseBody = HarUtils.harLogToByteArray(harLog);
                    } catch (ApiException e) {
                        responseBody =
                                e.toString(API.Format.JSON, incErrorDetails())
                                        .getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);

                        ApiException apiException =
                                new ApiException(ApiException.Type.INTERNAL_ERROR, e.getMessage());
                        responseBody =
                                apiException
                                        .toString(API.Format.JSON, incErrorDetails())
                                        .getBytes(StandardCharsets.UTF_8);
                    }
                }
            }

            try {
                msg.setResponseHeader(
                        API.getDefaultResponseHeader(
                                "application/json; charset=UTF-8", responseBody.length));
            } catch (HttpMalformedHeaderException e) {
                logger.error("Failed to create response header: " + e.getMessage(), e);
            }
            msg.setResponseBody(responseBody);

            return msg;
        } else if (OTHER_SCRIPT_JS.equals(name)) {
            try {
                msg.setResponseBody(API_SCRIPT);
                // Allow caching
                msg.setResponseHeader(
                        API.getDefaultResponseHeader("text/javascript", API_SCRIPT.length(), true));
                msg.getResponseHeader()
                        .addHeader(HttpResponseHeader.CACHE_CONTROL, API_SCRIPT_CACHE_CONTROL);
            } catch (HttpMalformedHeaderException e) {
                logger.error("Failed to create response header: " + e.getMessage(), e);
            }

            return msg;
        } else {
            throw new ApiException(ApiException.Type.BAD_OTHER);
        }
    }

    private static List<Integer> getIds(JSONObject params) throws ApiException {
        List<Integer> listIds = new ArrayList<>();
        for (String id : params.getString(PARAM_IDS).split(",")) {
            try {
                listIds.add(Integer.valueOf(id.trim()));
            } catch (NumberFormatException e) {
                throw new ApiException(ApiException.Type.ILLEGAL_PARAMETER, PARAM_IDS, e);
            }
        }

        if (listIds.isEmpty()) {
            throw new ApiException(ApiException.Type.MISSING_PARAMETER, PARAM_IDS);
        }
        return listIds;
    }

    /**
     * Adds the given history record to the given {@code entries}.
     *
     * @param entries where to add the new {@code HarEntry}.
     * @param recordHistory the history record to add, after converting to {@code HarEntry}.
     * @see HarUtils#createHarEntry(int, int, HttpMessage)
     */
    private static void addHarEntry(HarEntries entries, RecordHistory recordHistory) {
        entries.addEntry(
                HarUtils.createHarEntry(
                        recordHistory.getHistoryId(),
                        recordHistory.getHistoryType(),
                        recordHistory.getHttpMessage()));
    }

    private boolean incErrorDetails() {
        return Model.getSingleton().getOptionsParam().getApiParam().isIncErrorDetails();
    }

    private static void writeReportLastScanTo(HttpMessage msg, ScanReportType reportType)
            throws Exception {
        ReportLastScan rls = new ReportLastScan();
        StringBuilder report = new StringBuilder();
        rls.generate(report);

        String response;
        if (ScanReportType.XML == reportType) {
            // Copy as is
            msg.setResponseHeader(API.getDefaultResponseHeader("text/xml; charset=UTF-8"));
            response = report.toString();
        } else if (ScanReportType.MD == reportType) {
            msg.setResponseHeader(API.getDefaultResponseHeader("text/markdown; charset=UTF-8"));
            response = generateReportWithXsl(report.toString(), "report.md.xsl");
        } else if (ScanReportType.JSON == reportType) {
            msg.setResponseHeader(API.getDefaultResponseHeader("application/json; charset=UTF-8"));
            response = ReportGenerator.stringToJson(report.toString());
        } else {
            msg.setResponseHeader(API.getDefaultResponseHeader("text/html; charset=UTF-8"));
            response = generateReportWithXsl(report.toString(), "report.html.xsl");
        }

        msg.setResponseBody(response);
        msg.getResponseHeader().setContentLength(msg.getResponseBody().length());
    }

    private static String generateReportWithXsl(String report, String xslFileName)
            throws IOException {
        Path xslFile = Paths.get(Constant.getZapInstall(), "xml", xslFileName);
        if (Files.exists(xslFile)) {
            return ReportGenerator.stringToHtml(report, xslFile.toString());
        }

        String path = "/org/zaproxy/zap/resources/xml/" + xslFileName;
        try (InputStream is = ReportLastScan.class.getResourceAsStream(path)) {
            if (is == null) {
                logger.error("Bundled file not found: " + path);
                return "";
            }
            return ReportGenerator.stringToHtml(report, new StreamSource(is));
        }
    }

    @Override
    public HttpMessage handleShortcut(HttpMessage msg) throws ApiException {
        try {
            if (msg.getRequestHeader().getURI().getPath().startsWith("/" + OTHER_PROXY_PAC)) {
                return this.handleApiOther(msg, OTHER_PROXY_PAC, new JSONObject());
            } else if (msg.getRequestHeader()
                    .getURI()
                    .getPath()
                    .startsWith("/" + OTHER_SET_PROXY)) {
                JSONObject params = new JSONObject();
                params.put(PARAM_PROXY_DETAILS, msg.getRequestBody().toString());
                return this.handleApiOther(msg, OTHER_SET_PROXY, params);
            } else if (msg.getRequestHeader()
                    .getURI()
                    .getPath()
                    .startsWith("/" + OTHER_SCRIPT_JS)) {
                return this.handleApiOther(msg, OTHER_SCRIPT_JS, new JSONObject());
            }
        } catch (URIException e) {
            logger.error(e.getMessage(), e);
            throw new ApiException(ApiException.Type.INTERNAL_ERROR);
        }
        throw new ApiException(
                ApiException.Type.URL_NOT_FOUND, msg.getRequestHeader().getURI().toString());
    }

    private String getPacFile(String host, int port) {
        // Could put in 'ignore urls'?
        StringBuilder sb = new StringBuilder(100);
        sb.append("function FindProxyForURL(url, host) {\n");
        sb.append("  return \"PROXY ").append(host).append(':').append(port).append("\";\n");
        sb.append("} // End of function\n");

        return sb.toString();
    }

    private static void addUrlsToList(
            String baseUrl, SiteNode parent, Set<String> addedUrls, ApiResponseList list) {
        @SuppressWarnings("unchecked")
        Enumeration<TreeNode> en = parent.children();
        while (en.hasMoreElements()) {
            SiteNode child = (SiteNode) en.nextElement();
            String uri = child.getHistoryReference().getURI().toString();
            if (!addedUrls.contains(uri) && (baseUrl.isEmpty() || uri.startsWith(baseUrl))) {
                list.addItem(new ApiResponseElement("url", uri));
                addedUrls.add(uri);
            }

            addUrlsToList(baseUrl, child, addedUrls, list);
        }
    }

    private void processHttpMessages(
            String baseUrl, int start, int count, Processor<RecordHistory> processor)
            throws ApiException {
        try {
            TableHistory tableHistory = Model.getSingleton().getDb().getTableHistory();
            List<Integer> historyIds =
                    tableHistory.getHistoryIds(Model.getSingleton().getSession().getSessionId());

            PaginationConstraintsChecker pcc = new PaginationConstraintsChecker(start, count);
            for (Integer id : historyIds) {
                RecordHistory recHistory = tableHistory.read(id);

                HttpMessage msg = recHistory.getHttpMessage();

                if (msg.getRequestHeader().isImage() || msg.getResponseHeader().isImage()) {
                    continue;
                }

                if (baseUrl != null
                        && !msg.getRequestHeader().getURI().toString().startsWith(baseUrl)) {
                    // Not subordinate to the specified URL
                    continue;
                }

                pcc.recordProcessed();
                if (!pcc.hasPageStarted()) {
                    continue;
                }

                processor.process(recHistory);
                if (pcc.hasPageEnded()) {
                    break;
                }
            }
        } catch (HttpMalformedHeaderException | DatabaseException e) {
            logger.error(e.getMessage(), e);
            throw new ApiException(ApiException.Type.INTERNAL_ERROR);
        }
    }

    private interface Processor<T> {

        void process(T object);
    }

    private static class CounterProcessor<T> implements Processor<T> {

        private int count;

        public CounterProcessor() {
            count = 0;
        }

        @Override
        public void process(T object) {
            ++count;
        }

        public int getCount() {
            return count;
        }
    }

    @Override
    public void sessionOpened(File file, Exception e) {
        // Ignore
    }

    @Override
    public void sessionSaved(Exception e) {
        logger.debug("Saved session notification");
        this.savingSession = false;
    }

    @Override
    public void sessionSnapshot(Exception e) {
        logger.debug("Snapshot session notification");
        this.savingSession = false;
    }

    private static class PaginationConstraintsChecker {

        private boolean pageStarted;
        private boolean pageEnded;
        private final int startRecord;
        private final boolean hasEnd;
        private final int finalRecord;
        private int recordsProcessed;

        public PaginationConstraintsChecker(int start, int count) {
            recordsProcessed = 0;

            if (start > 0) {
                pageStarted = false;
                startRecord = start;
            } else {
                pageStarted = true;
                startRecord = 0;
            }

            if (count > 0) {
                hasEnd = true;
                finalRecord = !pageStarted ? start + count - 1 : count;
            } else {
                hasEnd = false;
                finalRecord = 0;
            }
            pageEnded = false;
        }

        public void recordProcessed() {
            ++recordsProcessed;

            if (!pageStarted) {
                pageStarted = recordsProcessed >= startRecord;
            }

            if (hasEnd && !pageEnded) {
                pageEnded = recordsProcessed >= finalRecord;
            }
        }

        public boolean hasPageStarted() {
            return pageStarted;
        }

        public boolean hasPageEnded() {
            return pageEnded;
        }
    }

    /**
     * A {@link HttpRedirectionValidator} that enforces the {@link Mode} when validating the {@code
     * URI} of redirections.
     *
     * @see #isRequestValid()
     */
    private static class ModeRedirectionValidator implements HttpRedirectionValidator {

        private final Processor<HttpMessage> processor;
        private boolean isRequestValid;

        public ModeRedirectionValidator(Processor<HttpMessage> processor) {
            this.processor = processor;
            this.isRequestValid = true;
        }

        @Override
        public void notifyMessageReceived(HttpMessage message) {
            persistMessage(message);
            processor.process(message);
        }

        @Override
        public boolean isValid(URI redirection) {
            isRequestValid = isValidForCurrentMode(redirection);
            return isRequestValid;
        }

        /**
         * Tells whether or not the request is valid, that is, all redirections were valid for the
         * current {@link Mode}.
         *
         * @return {@code true} is the request is valid, {@code false} otherwise.
         */
        public boolean isRequestValid() {
            return isRequestValid;
        }
    }
}
