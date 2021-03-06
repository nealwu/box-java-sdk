package com.box.sdk;

import java.net.MalformedURLException;
import java.net.URL;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.eclipsesource.json.JsonObject;

public class BoxAPIConnectionTest {
    @Test
    @Category(UnitTest.class)
    public void canRefreshWhenGivenRefreshToken() {
        final String anyClientID = "";
        final String anyClientSecret = "";
        final String anyAccessToken = "";
        final String anyRefreshToken = "";

        BoxAPIConnection api = new BoxAPIConnection(anyClientID, anyClientSecret, anyAccessToken, anyRefreshToken);

        assertThat(api.canRefresh(), is(true));
    }

    @Test
    @Category(UnitTest.class)
    public void needsRefreshWhenTokenHasExpired() {
        final String anyAccessToken = "";

        BoxAPIConnection api = new BoxAPIConnection(anyAccessToken);
        api.setExpires(-1);

        assertThat(api.needsRefresh(), is(true));
    }

    @Test
    @Category(UnitTest.class)
    public void doesNotNeedRefreshWhenTokenHasNotExpired() {
        final String anyAccessToken = "";

        BoxAPIConnection api = new BoxAPIConnection(anyAccessToken);
        api.setExpires(Long.MAX_VALUE);

        assertThat(api.needsRefresh(), is(not(true)));
    }

    @Test
    @Category(UnitTest.class)
    public void needsRefreshWhenExpiresIsZero() {
        final String anyAccessToken = "";

        BoxAPIConnection api = new BoxAPIConnection(anyAccessToken);
        api.setExpires(0);

        assertThat(api.needsRefresh(), is(true));
    }

    @Test
    @Category(UnitTest.class)
    public void interceptorReceivesSentRequest() throws MalformedURLException {
        BoxAPIConnection api = new BoxAPIConnection("");

        BoxAPIResponse fakeResponse = new BoxAPIResponse();

        RequestInterceptor mockInterceptor = mock(RequestInterceptor.class);
        when(mockInterceptor.onRequest(any(BoxAPIRequest.class))).thenReturn(fakeResponse);
        api.setRequestInterceptor(mockInterceptor);

        BoxAPIRequest request = new BoxAPIRequest(api, new URL("http://anyurl.com"), "GET");
        BoxAPIResponse response = request.send();

        assertThat(response, is(equalTo(fakeResponse)));
    }

    @Test
    @Category(UnitTest.class)
    public void restoreConnectionThatDoesNotNeedRefresh() {
        BoxAPIConnection api = new BoxAPIConnection("fake client ID", "fake client secret", "fake access token",
            "fake refresh token");
        api.setExpires(3600000L);
        api.setLastRefresh(System.currentTimeMillis());
        String state = api.save();

        final BoxAPIConnection restoredAPI = BoxAPIConnection.restore(state);
        restoredAPI.setRequestInterceptor(new RequestInterceptor() {
            @Override
            public BoxAPIResponse onRequest(BoxAPIRequest request) {
                String tokenURLString = restoredAPI.getTokenURL().toString();
                String requestURLString = request.getUrl().toString();
                if (requestURLString.contains(tokenURLString)) {
                    fail("The connection was refreshed.");
                }

                if (requestURLString.contains("folders")) {
                    return new BoxJSONResponse() {
                        @Override
                        public String getJSON() {
                            JsonObject responseJSON = new JsonObject()
                                .add("id", "fake ID")
                                .add("type", "folder");
                            return responseJSON.toString();
                        }
                    };
                }

                fail("Unexpected request.");
                return null;
            }
        });

        assertFalse(restoredAPI.needsRefresh());
    }

    @Test
    @Category(IntegrationTest.class)
    public void requestIsSentNormallyWhenInterceptorReturnsNullResponse() throws MalformedURLException {
        BoxAPIConnection api = new BoxAPIConnection("");

        RequestInterceptor mockInterceptor = mock(RequestInterceptor.class);
        when(mockInterceptor.onRequest(any(BoxAPIRequest.class))).thenReturn(null);
        api.setRequestInterceptor(mockInterceptor);

        BoxAPIRequest request = new BoxAPIRequest(api, new URL("http://box.com"), "GET");
        BoxAPIResponse response = request.send();

        assertThat(response.getResponseCode(), is(200));
    }

    @Test
    @Category(IntegrationTest.class)
    public void refreshSucceeds() {
        final String originalAccessToken = TestConfig.getAccessToken();
        final String originalRefreshToken = TestConfig.getRefreshToken();
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getClientID(), TestConfig.getClientSecret(),
            originalAccessToken, originalRefreshToken);

        api.refresh();

        String actualAccessToken = api.getAccessToken();
        String actualRefreshToken = api.getRefreshToken();

        assertThat(originalRefreshToken, not(equalTo(actualRefreshToken)));
        assertThat(originalAccessToken, not(equalTo(actualAccessToken)));

        TestConfig.setAccessToken(actualAccessToken);
        TestConfig.setRefreshToken(actualRefreshToken);
    }

    @Test
    @Category(IntegrationTest.class)
    public void refreshesWhenGetAccessTokenIsCalledAndTokenHasExpired() {
        final String originalAccessToken = TestConfig.getAccessToken();
        final String originalRefreshToken = TestConfig.getRefreshToken();
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getClientID(), TestConfig.getClientSecret(),
            originalAccessToken, originalRefreshToken);
        api.setExpires(-1);

        String actualAccessToken = api.getAccessToken();
        String actualRefreshToken = api.getRefreshToken();

        assertThat(originalRefreshToken, not(equalTo(actualRefreshToken)));
        assertThat(originalAccessToken, not(equalTo(actualAccessToken)));

        TestConfig.setAccessToken(actualAccessToken);
        TestConfig.setRefreshToken(actualRefreshToken);
    }

    @Test
    @Category(IntegrationTest.class)
    public void doesNotRefreshWhenGetAccessTokenIsCalledAndTokenHasNotExpired() {
        final String originalAccessToken = TestConfig.getAccessToken();
        final String originalRefreshToken = TestConfig.getRefreshToken();
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getClientID(), TestConfig.getClientSecret(),
            originalAccessToken, originalRefreshToken);
        api.setExpires(Long.MAX_VALUE);

        String actualAccessToken = api.getAccessToken();
        String actualRefreshToken = api.getRefreshToken();

        assertThat(originalRefreshToken, equalTo(actualRefreshToken));
        assertThat(originalAccessToken, equalTo(actualAccessToken));

        TestConfig.setAccessToken(actualAccessToken);
        TestConfig.setRefreshToken(actualRefreshToken);
    }

    @Test
    @Category(IntegrationTest.class)
    public void successfullySavesAndRestoresConnection() {
        final String originalAccessToken = TestConfig.getAccessToken();
        final String originalRefreshToken = TestConfig.getRefreshToken();
        BoxAPIConnection api = new BoxAPIConnection(TestConfig.getClientID(), TestConfig.getClientSecret(),
            originalAccessToken, originalRefreshToken);
        String state = api.save();

        BoxAPIConnection restoredAPI = BoxAPIConnection.restore(state);
        BoxFolder.Info rootFolderInfo = BoxFolder.getRootFolder(restoredAPI).getInfo();

        TestConfig.setAccessToken(restoredAPI.getAccessToken());
        TestConfig.setRefreshToken(restoredAPI.getRefreshToken());
    }
}
