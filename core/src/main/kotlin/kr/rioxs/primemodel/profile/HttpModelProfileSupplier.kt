package kr.rioxs.primemodel.profile
import kr.rioxs.primemodel.api.PrimeModel
import kr.rioxs.primemodel.api.manager.Managers.Manager

import com.github.benmanes.caffeine.cache.Caffeine
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mojang.authlib.properties.PropertyMap
import com.mojang.util.UUIDTypeAdapter
import java.io.Reader
import java.net.http.HttpResponse
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kr.rioxs.primemodel.api.platform.PlatformPlayer
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfile
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileInfo
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSkin
import kr.rioxs.primemodel.api.profile.Profiles.ModelProfileSupplier
import kr.rioxs.primemodel.manager.ProfileManagerImpl
import kr.rioxs.primemodel.util.buildHttpRequest
import kr.rioxs.primemodel.util.handleException
import kr.rioxs.primemodel.util.httpClient
import kr.rioxs.primemodel.util.PLATFORM

class DefaultHttpModelProfileSupplier : ModelProfileSupplier {

    private val http = HttpModelProfileSupplier()

    override fun supply(info: ModelProfileInfo): ModelProfile.Uncompleted {
        val player = PLATFORM.adapter().offlinePlayer(info.id)
        return if (player is PlatformPlayer) ModelProfile.of(player).asUncompleted() else http.supply(info)
    }
}

class HttpModelProfileSupplier : ModelProfileSupplier {

    private val profileCache = Caffeine.newBuilder()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build<ModelProfileInfo, ModelProfile>()

    private val serializer = GsonBuilder()
        .registerTypeAdapter(UUID::class.java, UUIDTypeAdapter())
        .registerTypeAdapter(PropertyMap::class.java, PropertyMap.Serializer())
        .create()

    private data class Profile(
        val id: UUID,
        val name: String,
        val properties: PropertyMap
    )

    private fun read(reader: Reader) = serializer.fromJson(reader, Profile::class.java)

    override fun supply(info: ModelProfileInfo): ModelProfile.Uncompleted {
        return object : ModelProfile.Uncompleted {
            override fun info(): ModelProfileInfo = info

            override fun complete(): CompletableFuture<ModelProfile> {
                return profileCache.getIfPresent(info)?.let { CompletableFuture.completedFuture(it) } ?: httpClient {
                    (info.name?.let {
                        sendAsync(
                            buildHttpRequest {
                                GET()
                                uri(URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/${it}"))
                            },
                            HttpResponse.BodyHandlers.ofInputStream()
                        ).thenApply { body ->
                            body.body().use { body ->
                                body.reader().use(JsonParser::parseReader)
                            }.asJsonObject
                                .getAsJsonPrimitive("id")
                                .asString
                        }
                    } ?: CompletableFuture.completedFuture(info.id.toString().replace("-", ""))).thenComposeAsync {
                        sendAsync(
                            buildHttpRequest {
                                GET()
                                uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$it"))
                            },
                            HttpResponse.BodyHandlers.ofInputStream()
                        )
                    }.thenApplyAsync {
                        it.body().use { body ->
                            body.reader().use(::read)
                        }.let { profile ->
                            ModelProfile.of(
                                ModelProfileInfo(profile.id, profile.name),
                                profile.properties["textures"].firstOrNull()?.let { property ->
                                    ProfileManagerImpl.skin(property.value)
                                } ?: ModelProfileSkin.EMPTY
                            ).apply {
                                profileCache.put(info, this)
                            }
                        }
                    }.exceptionally {
                        it.handleException("Unable to get ${info.name}'s skin data.")
                        fallback()
                    }
                }.orElse {
                    it.handleException("Unable to get ${info.name}'s user data.")
                    CompletableFuture.completedFuture(fallback())
                }
            }
        }
    }
}
