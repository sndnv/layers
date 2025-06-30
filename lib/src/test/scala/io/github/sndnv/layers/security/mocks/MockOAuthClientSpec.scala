package io.github.sndnv.layers.security.mocks

import io.github.sndnv.layers.security.oauth.OAuthClient
import io.github.sndnv.layers.security.oauth.OAuthClient.GrantParameters
import io.github.sndnv.layers.testing.UnitSpec

class MockOAuthClientSpec extends UnitSpec {
  "A MockOAuthClient" should "support providing tokens" in {
    val token = OAuthClient.AccessTokenResponse(access_token = "token", refresh_token = None, expires_in = 1, scope = None)
    val client = new MockOAuthClient(token = Some(token))

    client.tokenEndpoint should be("MockOAuthClient")

    client.clientCredentialsTokensProvided should be(0)
    client.refreshTokensProvided should be(0)
    client.passwordTokensProvided should be(0)
    client.tokensProvided should be(0)

    for {
      clientCredentialsToken <- client.token(
        scope = None,
        parameters = GrantParameters.ClientCredentials()
      )
      refreshTokenToken <- client.token(
        scope = None,
        parameters = GrantParameters.RefreshToken(refreshToken = "test")
      )
      resourceOwnerPasswordCredentialsToken <- client.token(
        scope = None,
        parameters = GrantParameters.ResourceOwnerPasswordCredentials(username = "a", password = "b")
      )
    } yield {
      clientCredentialsToken should be(token)
      refreshTokenToken should be(token)
      resourceOwnerPasswordCredentialsToken should be(token)

      client.clientCredentialsTokensProvided should be(1)
      client.refreshTokensProvided should be(1)
      client.passwordTokensProvided should be(1)
      client.tokensProvided should be(3)
    }
  }

  it should "fail to provide tokens when a token is not configured" in {
    val client = new MockOAuthClient(token = None)

    client.tokenEndpoint should be("MockOAuthClient")

    client.clientCredentialsTokensProvided should be(0)
    client.refreshTokensProvided should be(0)
    client.passwordTokensProvided should be(0)
    client.tokensProvided should be(0)

    client.token(scope = None, parameters = GrantParameters.ClientCredentials()).failed.map { e =>
      e.getMessage should be("No token response is available")

      client.clientCredentialsTokensProvided should be(1)
      client.refreshTokensProvided should be(0)
      client.passwordTokensProvided should be(0)
      client.tokensProvided should be(1)
    }
  }
}
