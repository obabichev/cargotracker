package org.eclipse.cargotracker.interfaces.booking.rest

import jakarta.ejb.Stateless
import jakarta.ws.rs.CookieParam
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.FormParam
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.MatrixParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType

/**
 * Illustrative JAX-RS resource: shows that Kotlin default values on `@QueryParam`,
 * `@HeaderParam`, `@FormParam`, `@CookieParam`, and `@MatrixParam` are silently ignored
 * by the JAX-RS runtime. See KTIJ ticket for the IDE inspection this file motivates.
 *
 * Each `broken*` method reads like the correct Kotlin idiom but is not; only `correct()`
 * behaves the way a reader would expect.
 */
@Stateless
@Path("/cargo-search")
class CargoSearchResource {

    // BROKEN — primitive Int with Kotlin default.
    //   GET /cargo-search/query-broken?query=widget          → limit == 0, not 20 (empty page)
    //   GET /cargo-search/query-broken?query=widget&limit=5  → limit == 5
    @GET
    @Path("/query-broken")
    @Produces(MediaType.APPLICATION_JSON)
    fun byQueryBroken(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int = 20,
    ): List<String> = fake(query, limit)

    // BROKEN — nullable + Elvis. Reads well, still wrong.
    //   GET /cargo-search/query-nullable?query=widget        → limit == null → 20 (works!)
    //   GET /cargo-search/query-nullable?query=widget&limit= → provider-side NumberFormatException
    //   because the runtime parses "" as Int before the Elvis fallback ever runs.
    @GET
    @Path("/query-nullable")
    @Produces(MediaType.APPLICATION_JSON)
    fun byQueryNullable(
        @QueryParam("query") query: String?,
        @QueryParam("limit") limit: Int? = null,
    ): List<String> = fake(query, limit ?: 20)

    // BROKEN — same trap on @HeaderParam.
    //   GET /cargo-search/header-broken with no `Accept-Language` header → locale == null, not "en".
    @GET
    @Path("/header-broken")
    @Produces(MediaType.APPLICATION_JSON)
    fun byHeaderBroken(
        @HeaderParam("Accept-Language") locale: String = "en",
    ): List<String> = fake(null, 5, locale = locale)

    // BROKEN — same trap on @CookieParam and @FormParam together on a POST.
    //   POST /cargo-search/form-broken with no body fields → both defaults ignored.
    @POST
    @Path("/form-broken")
    @Produces(MediaType.APPLICATION_JSON)
    fun byFormBroken(
        @FormParam("query") query: String = "*",
        @FormParam("limit") limit: Int = 20,
        @CookieParam("session-hint") sessionHint: String = "",
    ): List<String> = fake(query, limit, sessionHint = sessionHint)

    // BROKEN — same trap on @MatrixParam.
    //   GET /cargo-search/matrix-broken;page=3 → page == 3
    //   GET /cargo-search/matrix-broken        → page == 0, not 1
    @GET
    @Path("/matrix-broken")
    @Produces(MediaType.APPLICATION_JSON)
    fun byMatrixBroken(
        @MatrixParam("page") page: Int = 1,
    ): List<String> = fake(null, page)

    // CORRECT — same intent expressed the Jakarta-native way. `@DefaultValue` is applied
    // by the JAX-RS runtime *before* reflective method invocation, so it works uniformly
    // for missing values and empty-string values.
    @GET
    @Path("/correct")
    @Produces(MediaType.APPLICATION_JSON)
    fun correct(
        @QueryParam("query") query: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int,
        @HeaderParam("Accept-Language") @DefaultValue("en") locale: String,
        @CookieParam("session-hint") @DefaultValue("") sessionHint: String,
    ): List<String> = fake(query, limit, locale = locale, sessionHint = sessionHint)

    private fun fake(
        query: String?,
        limit: Int,
        locale: String? = null,
        sessionHint: String? = null,
    ): List<String> = List(limit) { "cargo-$it query=$query locale=$locale session=$sessionHint" }
}
