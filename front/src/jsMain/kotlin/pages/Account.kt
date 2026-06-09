package pages

import client
import components.Header
import components.SkeletonSpan
import emotion.react.Global
import emotion.react.css
import emotion.react.styles
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import js.buffer.ArrayBuffer
import js.objects.unsafeJso
import js.promise.await
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mui.icons.material.Add
import mui.material.*
import mui.system.Breakpoint
import mui.system.responsive
import mui.system.sx
import org.webctc.common.types.mc.PlayerPrincipal
import org.webctc.common.types.mc.PlayerProfile
import org.webctc.common.types.webauthn.WebAuthnRegistration
import org.webctc.common.types.webauthn.WebAuthnRegistrationOption
import react.FC
import react.create
import react.dom.html.ReactHTML.body
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.useState
import tanstack.react.router.useNavigate
import tanstack.router.core.RoutePath
import utils.useData
import web.authn.*
import web.credentials.CredentialCreationOptions
import web.cssom.*
import web.navigator.navigator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

inline var GridProps.xs: Any?
    get() {
        return asDynamic().xs
    }
    set(value) {
        asDynamic().xs = value
    }

inline var GridProps.md: Any?
    get() {
        return asDynamic().md
    }
    set(value) {
        asDynamic().md = value
    }

data class Service(
    val name: String,
    val description: String,
    val path: String,
)

val serviceList = listOf(
    Service(
        "MapViewer",
        "서버 전체의 선로, 열차, 신호 등을 지도로 조감하여 볼 수 있습니다",
        "/",
    ),
    Service(
        "RailGroup Manager",
        "레일을 그룹으로 묶어, 열차를 레드스톤 블록/유리 블록으로 Minecraft 내에 설치하는 기구와 신호 제어를 구성할 수 있습니다",
        "/p/railgroup",
    ),
    Service(
        "WayPoint Editor",
        "WayPoint를 편집할 수 있습니다",
        "/p/waypoint",
    ),
    Service(
        "TeCon Editor",
        "연동 장치를 열람 및 편집할 수 있습니다",
        "/p/tecons",
    ),
)

val Account = FC {

    val playerPrincipal by useData<PlayerPrincipal>("/auth/profile")
    val playerUuid = playerPrincipal?.uuid

    val playerProfile by useData<PlayerProfile>(playerUuid?.let { "https://mc-heads.net/json/get_user?u=$it" })

    var passkeyResult by useState<String>()
    var passkeyDialogOpen by useState(false)

    val navigate = useNavigate()

    CssBaseline {}
    Header {}
    Container {
        maxWidth = Breakpoint.md
        Card {
            sx {
                padding = 16.px
            }
            h1 { +"アカウント情報" }
            Box {
                css {
                    display = Display.flex
                    alignItems = AlignItems.center
                }

                Box {
                    css {
                        paddingRight = 1.5.rem
                    }
                    if (playerUuid.isNullOrEmpty()) {
                        Skeleton {
                            variant = SkeletonVariant.circular
                            width = 5.rem
                            height = 5.rem
                        }
                    } else {
                        img {
                            src = "https://mc-heads.net/avatar/$playerUuid"
                            style = unsafeJso {
                                width = 5.rem
                                height = 5.rem
                            }
                        }
                    }
                }

                Box {
                    p {
                        SkeletonSpan {
                            prefix = "MCID"
                            width = 10.rem
                            text = playerProfile?.username
                        }
                    }
                    p {
                        SkeletonSpan {
                            prefix = "UUID"
                            width = 10.rem
                            text = playerUuid
                        }
                    }
                }
            }

            h1 { +"서비스 목록" }
            Box {
                p { +"이 서버에서는 다음 서비스를 이용할 수 있습니다." }

                Grid {
                    container = true
                    spacing = responsive(3)
                    serviceList.forEach { service ->
                        Grid {
                            item = true
                            xs = 12
                            md = 6
                            Card {
                                sx {
                                    height = 100.pct
                                    display = Display.flex
                                    flexDirection = FlexDirection.column
                                    justifyContent = JustifyContent.spaceBetween
                                }
                                CardContent {
                                    h1 { +service.name }
                                    p { +service.description }
                                }
                                CardActions {
                                    Button {
                                        +"사용하기"
                                        variant = ButtonVariant.contained
                                        color = ButtonColor.primary
                                        onClick = { navigate { to = RoutePath(service.path) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            h1 { +"Passkey" }
            Box {
                p { +"WebCTC는 Passkey 로그인을 지원합니다. (※SSL 접속 시)" }
                p { +"Passkey를 이용하면, Minecraft 서버에서 명령어로 세션 URL을 발급받지 않고 로그인할 수 있습니다." }
                Button {
                    +"Passkey 추가"
                    variant = ButtonVariant.contained
                    color = ButtonColor.primary
                    startIcon = Add.create()
                    onClick = {
                        passkeyDialogOpen = true
                        passkeyResult = "Passkey 등록 중..."
                        MainScope().launch {
                            try {
                                val pubKey: WebAuthnRegistrationOption = client.post("/auth/webauthn/challenge").body()
                                val credential =
                                    navigator.credentials.createAsync(pubKey.toOption()).await() as? PublicKeyCredential
                                        ?: throw Exception("Credential is null")

                                val credentialData = credential.response as AuthenticatorAttestationResponse
                                val clientDataJSON = credentialData.clientDataJSON
                                val attestationObject = credentialData.attestationObject
                                val id = credential.id

                                val passKey = WebAuthnRegistration(
                                    id, attestationObject.toBase64Url(), clientDataJSON.toBase64Url(),
                                )
                                client.post("/auth/webauthn/register") {
                                    contentType(ContentType.Application.Json)
                                    setBody(passKey)
                                }.let {
                                    if (it.status == HttpStatusCode.OK) {
                                        passkeyResult =
                                            "Passkey 등록이 완료되었습니다. 이제 Minecraft 서버에서 명령어로 세션 URL을 발급받지 않고 Passkey를 이용하여 로그인할 수 있습니다."
                                        return@launch
                                    }
                                }
                            } catch (e: Throwable) {
                                e.printStackTrace()
                            }
                            passkeyResult = "Passkey 등록에 실패했습니다."
                        }
                    }

                }
            }

            Dialog {
                onClose = { _, _ -> passkeyDialogOpen = false }
                fullWidth = true
                maxWidth = Breakpoint.sm
                open = passkeyDialogOpen
                DialogTitle { +"Passkey 등록" }
                DialogContent { DialogContentText { +passkeyResult } }
            }
        }
    }

    Global {
        styles {
            body {
                backgroundColor = Color("#fafcfe")
            }
        }
    }
}

fun WebAuthnRegistrationOption.toOption(): CredentialCreationOptions {
    val kotlinData = this

    return CredentialCreationOptions(
        publicKey = PublicKeyCredentialCreationOptions(

            timeout = kotlinData.timeout,
            challenge = kotlinData.challenge.toBuffer(),
            rp = PublicKeyCredentialRpEntity(
                id = kotlinData.rp.id,
                name = kotlinData.rp.name
            ),
            user = PublicKeyCredentialUserEntity(
                id = kotlinData.user.id.toBuffer(),
                name = kotlinData.user.name,
                displayName = kotlinData.user.displayName,
            ),
            authenticatorSelection = AuthenticatorSelectionCriteria(
                requireResidentKey = true
            ),
            pubKeyCredParams = kotlinData.pubKeyCredParams.map {
                PublicKeyCredentialParameters(
                    type = PublicKeyCredentialType.publicKey,
                    alg = it.alg
                )
            }.toTypedArray()
        ),
    )
}

@OptIn(ExperimentalEncodingApi::class)
fun String.toBuffer(): ArrayBuffer {
    val byteArray = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(this)

    return byteArray.toUint8Array().buffer.unsafeCast<ArrayBuffer>()
}

fun ArrayBuffer.toByteArray(): ByteArray = Uint8Array(this).toByteArray()

@OptIn(ExperimentalEncodingApi::class)
fun ArrayBuffer.toBase64Url(): String {
    val byteArray = this.toByteArray()

    return Base64.UrlSafe.encode(byteArray)
}