package fr.techad.sonar.gerrit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import fr.techad.sonar.GerritPluginException;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GerritFacade {
    private static final Logger LOG = LoggerFactory.getLogger(GerritFacade.class);
    private static final String RESPONSE_PREFIX = ")]}'";
    private static final String COMMIT_MSG = "/COMMIT_MSG";
    private static final String MAVEN_ENTRY_REGEX = ".*src/";

    private static final String ERROR_LISTING = "Error listing files";
    private static final String ERROR_SETTING = "Error setting review";
    private GerritConnector gerritConnector;
    private ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, String> gerritFileList = new HashMap<String, String>();

    public GerritFacade(@NotNull String host, int port, @NotNull String username, @NotNull String password) {
        this("http", host, port, username, password, null, "digest");
    }

    public GerritFacade(String scheme, @NotNull String host, int port, @NotNull String username,
            @NotNull String password, @Nullable String basePath, @NotNull String authScheme) {
        gerritConnector = new GerritConnector(scheme, host, port, username, password, basePath, authScheme);
    }

    /**
     * @return sonarLongName to gerritFileName map
     */
    @NotNull
    public Map<String, String> listFiles(@NotNull String projectName, @NotNull String branchName,
            @NotNull String changeId, @NotNull String revisionId) throws GerritPluginException {
        if (!gerritFileList.isEmpty()) {
            LOG.debug("[GERRIT PLUGIN] File list already filled. Not calling Gerrit.");
        } else {
            try {
                String response = gerritConnector.listFiles(projectName, branchName, changeId, revisionId);
                String jsonString = trimResponse(response);
                ListFilesResponse listFilesResponse = objectMapper.readValue(jsonString, ListFilesResponse.class);
                Set<String> keys = listFilesResponse.keySet();
                keys.remove(COMMIT_MSG);
                for (String key : keys) {
                    gerritFileList.put(parseFileName(key), key);
                }
            } catch (IOException e) {
                throw new GerritPluginException(ERROR_LISTING, e);
            }
        }
        return Collections.unmodifiableMap(gerritFileList);
    }

    public void setReview(@NotNull String projectName, @NotNull String branchName, @NotNull String changeId,
            @NotNull String revisionId, @NotNull ReviewInput reviewInput) throws GerritPluginException {
        try {
            String json = objectMapper.writeValueAsString(reviewInput);
            gerritConnector.setReview(projectName, branchName, changeId, revisionId, json);
        } catch (JsonProcessingException e) {
            throw new GerritPluginException(ERROR_SETTING, e);
        } catch (IOException e) {
            throw new GerritPluginException(ERROR_SETTING, e);
        }
    }

    @NotNull
    protected String trimResponse(@NotNull String response) {
        return StringUtils.replaceOnce(response, RESPONSE_PREFIX, "");
    }

    protected String parseFileName(@NotNull String fileName) {
        return fileName.replaceFirst(MAVEN_ENTRY_REGEX, "src/");
    }

    void setGerritConnector(@NotNull GerritConnector gerritConnector) {
        this.gerritConnector = gerritConnector;
    }
}