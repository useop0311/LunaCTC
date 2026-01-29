package pages

import client
import components.Header
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import js.objects.jso
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mui.material.*
import mui.system.Breakpoint
import mui.system.sx
import org.webctc.common.types.webauthn.WebAuthnAuthentication
import org.webctc.common.types.webauthn.WebAuthnAuthenticationOption
import react.FC
import react.create
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.useState
import web.authn.AuthenticatorAssertionResponse
import web.authn.PublicKeyCredential
import web.authn.PublicKeyCredentialRequestOptions
import web.authn.UserVerificationRequirement
import web.credentials.CredentialRequestOptions
import web.cssom.Color
import web.cssom.px
import web.navigator.navigator

val Login = FC {
    var errorText by useState("")

    CssBaseline {}
    Header {}
    Container {
        maxWidth = Breakpoint.md
        Card {
            sx {
                padding = 16.px
            }
            h1 {
                +"WebCTC 로그인"
            }

            p {
                +"WebCTC에서는 사전에 등록한 Passkey로 로그인할 수 있습니다."
                +"Passkey를 등록하지 않은 경우, 서버 내에서 /webctc auth를 실행하고 표시된 URL에 접속해 주십시오."
            }

            Button {
                color = ButtonColor.primary
                variant = ButtonVariant.contained
                fullWidth = true
                startIcon = mui.icons.material.Login.create()
                +"Passkey로 로그인"
                onClick = {
                    MainScope().launch {
                        try {
                            val options: WebAuthnAuthenticationOption =
                                client.post("/auth/webauthn/auth-challenge").body()

                            val webOptions = options.toOptions()
                            val credential = navigator.credentials.getAsync(webOptions).await() as? PublicKeyCredential
                                ?: throw Exception("credential is null")

                            val credentialData = credential.response as AuthenticatorAssertionResponse
                            val clientDataJSON = credentialData.clientDataJSON
                            val authenticatorData = credentialData.authenticatorData
                            val signature = credentialData.signature
                            val userHandle = credentialData.userHandle
                            val id = credential.id

                            val authentication = WebAuthnAuthentication(
                                id,
                                authenticatorData.toBase64Url(),
                                clientDataJSON.toBase64Url(),
                                signature.toBase64Url(),
                                userHandle?.toBase64Url()
                            )

                            client.post("/auth/webauthn/authenticate") {
                                contentType(ContentType.Application.Json)
                                setBody(authentication)
                            }.let {
                                if (it.status == HttpStatusCode.OK) {
                                    window.location.href = "/p/account"
                                    return@launch
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        errorText = "로그인에 실패했습니다."
                    }
                }
            }


            p {
                css {
                    color = Color("red")
                }
                +errorText
            }
        }
    }

    Global {
        styles {
            ReactHTML.body {
                backgroundColor = Color("#fafcfe")
            }
        }
    }
}

fun WebAuthnAuthenticationOption.toOptions(): CredentialRequestOptions {
    val kotlinData = this
    return CredentialRequestOptions(
        publicKey = PublicKeyCredentialRequestOptions(
            challenge = kotlinData.challenge.toBuffer(),
            userVerification = UserVerificationRequirement.preferred,
            rpId = kotlinData.rpId,
            extensions = jso { },
            timeout = kotlinData.timeout,
            allowCredentials = arrayOf()
        )
    )
}