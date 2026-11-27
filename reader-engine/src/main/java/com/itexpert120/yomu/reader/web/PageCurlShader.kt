package com.itexpert120.yomu.reader.web

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import kotlin.math.PI
import kotlin.math.sin

/**
 * The page curl as one fragment shader over the page's area (see [CurlShape] for the model). Every
 * pixel asks, in the curl's canonical coordinates, what lies on top of it:
 *
 * 1. the curled paper coming back over the top, upside down — the leaf's back (its own text faintly
 *    through the paper on a single page, the next page's text in a spread), lit by how it faces up;
 * 2. else the paper rising into the curl, or still lying flat — the leaf's front, darkening as it
 *    rises away from the light;
 * 3. else the page underneath — the other page of a spread, or the page being turned to — darkened
 *    by the curl's soft shadow.
 *
 * Underneath may be left transparent: the live page (already moved to where the turn goes) shows
 * through. Edges are antialiased from the leaf's signed distance.
 */
@RequiresApi(33)
internal class PageCurlShader {
    val shader = RuntimeShader(SOURCE)

    private val empty = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private var boundFront: Bitmap? = null
    private var boundBack: Bitmap? = null
    private var boundStill: Bitmap? = null
    private var boundUnder: Bitmap? = null

    init {
        shader.setInputShader("front", empty)
        shader.setInputShader("back", empty)
        shader.setInputShader("still", empty)
        shader.setInputShader("under", empty)
    }

    fun update(frame: CurlFrame) {
        bind("front", frame.front, boundFront) { boundFront = it }
        bind("back", frame.back, boundBack) { boundBack = it }
        bind("still", frame.still, boundStill) { boundStill = it }
        bind("under", frame.under, boundUnder) { boundUnder = it }
        shader.setFloatUniform("underTex", if (frame.under != null) 1f else 0f)
        val s = frame.shape
        shader.setFloatUniform("size", frame.width, frame.height)
        shader.setFloatUniform("mirror", if (frame.mirror) 1f else 0f)
        shader.setFloatUniform("hinge", s.hinge)
        shader.setFloatUniform("leafW", s.leafWidth)
        shader.setFloatUniform("normal", s.normalX, s.normalY)
        shader.setFloatUniform("axis", s.axisX, s.axisY)
        shader.setFloatUniform("radius", s.radius)
        shader.setFloatUniform("frontLive", if (frame.frontLive) 1f else 0f)
        shader.setFloatUniform("frontReady", if (frame.front != null) 1f else 0f)
        shader.setFloatUniform("backMode", if (frame.backIsNextPage) 1f else 0f)
        shader.setFloatUniform("backReady", if (frame.back != null) 1f else 0f)
        shader.setFloatUniform(
            "stillMode",
            frame.still.let {
                if (it == null) {
                    0f
                } else if (frame.stillEverywhere) {
                    2f
                } else {
                    1f
                }
            },
        )
        shader.setFloatUniform("underReady", if (frame.underLive) 1f else 0f)
        shader.setColorUniform("paper", frame.paper)
        shader.setFloatUniform("dark", if (frame.dark) 1f else 0f)
        // The shadow grows in with the lift, so a barely lifted page casts none.
        val lift = (s.distance / (s.leafWidth * 0.08f)).coerceIn(0f, 1f) *
            ((1f - s.progress) / 0.08f).coerceIn(0f, 1f)
        shader.setFloatUniform("shadowAmount", lift)
        // Paper held at one height leads there and trails above and below it: the fold bends back
        // away from the grip (most mid-turn, none at either end, so a page at rest lies flat).
        val corner = s.cornerY <= 0f || s.cornerY >= s.height
        val bend = s.leafWidth * (if (corner) CORNER_BEND else MIDDLE_BEND) * sin(PI.toFloat() * s.progress)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("grabY", s.cornerY)
        // Outside the band where the curl draws anything (its roll, the paper turned back over the
        // page, their shadows), a pixel is only the page beside or under it: the shader returns that
        // at once there instead of working the whole curl out for every pixel of the window.
        val r = maxOf(s.radius, 0.0001f)
        val shadowTail = if (lift > 0f) (16f + r * 1.3f) * SHADOW_TAIL_FALLOFFS else 0f
        shader.setFloatUniform("plainBeyond", r + shadowTail + 2f)
        // The paper turned back over the page reaches down to where the leaf's farthest free-edge
        // corner lands; its contact shadow a little past that.
        val freeX = s.hinge + s.leafWidth
        val reach = maxOf(
            (freeX - s.axisX) * s.normalX + (0f - s.axisY) * s.normalY,
            (freeX - s.axisX) * s.normalX + (s.height - s.axisY) * s.normalY,
        )
        shader.setFloatUniform("plainBefore", minOf(0f, PI.toFloat() * r - reach - (2f + r * 0.08f) * CONTACT_FALLOFFS - 2f))
    }

    private inline fun bind(name: String, bitmap: Bitmap?, bound: Bitmap?, remember: (Bitmap?) -> Unit) {
        if (bitmap === bound) return
        shader.setInputShader(
            name,
            if (bitmap == null) {
                empty
            } else {
                BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }
            },
        )
        remember(bitmap)
    }

    private companion object {
        // How many of their own falloffs the cast and contact shadows are followed for: past that
        // they darken the page by less than one step of 255.
        const val SHADOW_TAIL_FALLOFFS = 5.2f
        const val CONTACT_FALLOFFS = 5.3f

        // How far the fold trails at the page's height away from the grip (squared share of the
        // height), as a share of the leaf's width mid-turn: held mid-edge, and by a corner.
        const val MIDDLE_BEND = 0.32f
        const val CORNER_BEND = 0.1f

        val SOURCE = """
            uniform shader front;
            uniform shader back;
            uniform shader still;
            uniform shader under;
            uniform float underTex;
            uniform float2 size;
            uniform float mirror;
            uniform float hinge;
            uniform float leafW;
            uniform float2 normal;
            uniform float2 axis;
            uniform float radius;
            uniform float frontLive;
            uniform float frontReady;
            uniform float backMode;
            uniform float backReady;
            uniform float stillMode;
            uniform float underReady;
            layout(color) uniform half4 paper;
            uniform float dark;
            uniform float shadowAmount;
            uniform float plainBefore;
            uniform float plainBeyond;
            uniform float bend;
            uniform float grabY;

            const float PI = 3.14159265;

            float2 toReal(float2 c) { return mirror > 0.5 ? float2(size.x - c.x, c.y) : c; }

            // Signed distance from c to the leaf (negative inside), in px.
            float leafSdf(float2 c) {
                float2 lo = float2(hinge, 0.0);
                float2 hi = float2(hinge + leafW, size.y);
                float2 d = abs(c - (lo + hi) * 0.5) - (hi - lo) * 0.5;
                return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
            }

            half4 frontAt(float2 c) {
                return frontReady > 0.5 ? half4(front.eval(toReal(c)).rgb, 1.0) : paper;
            }

            half4 backAt(float2 c) {
                if (backMode < 0.5) {
                    // A single page: its own text faintly through the paper, mirrored.
                    return half4(mix(paper.rgb, frontAt(c).rgb, half(0.13)), 1.0);
                }
                if (backReady < 0.5) return paper;
                // A spread: the back of this page is the next spread's facing page.
                return half4(back.eval(toReal(float2(2.0 * hinge - c.x, c.y))).rgb, 1.0);
            }

            half4 main(float2 fragCoord) {
                // On a dark page shading can't show (black stays black however much it is darkened),
                // so the leaf is drawn with light instead: the paper lifted toward it, a sheen along
                // the roll, a fine light edge. Most on pure black, less as the page gets lighter.
                float night = dark > 0.5 ? 1.0 - clamp(dot(float3(paper.rgb), float3(0.2126, 0.7152, 0.0722)) / 0.5, 0.0, 0.6) : 0.0;
                float2 p = mirror > 0.5 ? float2(size.x - fragCoord.x, fragCoord.y) : fragCoord;
                bool inStill = stillMode > 1.5 || (stillMode > 0.5 && p.x < hinge);
                // Underneath: the page beside the leaf, else the page the turn goes to — its snapshot
                // once taken (so the overlay is opaque), the live page until then, plain paper
                // before the jump.
                half4 color = inStill ? half4(still.eval(fragCoord).rgb, 1.0)
                    : underTex > 0.5 ? half4(under.eval(fragCoord).rgb, 1.0)
                    : (underReady > 0.5 ? half4(0.0) : paper);

                float R = max(radius, 0.0001);
                // Distance past the fold, which bends back away from the grip's height.
                float dy = (p.y - grabY) / size.y;
                float d = dot(p - axis, normal) - bend * dy * dy;

                // Past the curl and its shadow: only the page underneath.
                if (d >= plainBeyond) return color;
                // Before the paper turned back over the page reaches: the leaf lying flat.
                if (d < plainBefore) {
                    float flatCover = clamp(0.5 - leafSdf(p), 0.0, 1.0);
                    half4 flatColor = (frontLive > 0.5 && frontReady < 0.5) ? half4(0.0) : frontAt(p);
                    return mix(color, flatColor, half(flatCover));
                }

                // The angle round the roll, for a point over it.
                float th = (d >= 0.0 && d < R) ? asin(clamp(d / R, 0.0, 1.0)) : 0.0;

                // The paper rising into the curl, or lying flat.
                float sLow = d;
                float lowLight = 1.0;
                float lowLift = 0.0;
                bool low = d < R;
                if (d >= 0.0 && low) {
                    sLow = R * th;
                    // Rising away from the light, the inside of the curl deepens into its crease
                    // (gently on a dark page, which has little light to lose).
                    lowLight = 1.0 - (dark > 0.5 ? 0.28 : 0.4) * (1.0 - pow(cos(th), 1.4));
                    // On a dark page the rising paper catches light toward the roll instead.
                    lowLift = night * 0.07 * pow(sin(th), 1.5);
                }
                if (low) {
                    float2 q = p + (sLow - d) * normal;
                    float cover = clamp(0.5 - leafSdf(q), 0.0, 1.0);
                    half4 lowColor = (frontLive > 0.5 && d < 0.0 && frontReady < 0.5) ? half4(0.0)
                        : half4(half3(clamp(float3(frontAt(q).rgb) * lowLight + lowLift, 0.0, 1.0)), 1.0);
                    color = mix(color, lowColor, half(cover));
                }

                // The paper coming back over the top.
                float sTop;
                // How much the paper on top faces up: fully on the flat part lying over the page and
                // on the crest, less down the side of the roll. One formula for both, so there's no
                // seam where they meet.
                float z = 1.0;
                if (d < 0.0) {
                    sTop = PI * R - d;
                } else {
                    sTop = R * (PI - th);
                    z = cos(th);
                }
                // Matte paper lit from above. A dark page lifts a little toward the light instead.
                float topLight = dark > 0.5 ? 1.0 + 0.1 * pow(z, 3.0) - 0.12 * (1.0 - z) : 1.0 - 0.24 * (1.0 - pow(z, 0.8));
                float gloss = pow(z, 4.0) * 0.12;
                float2 qTop = p + (sTop - d) * normal;
                float topSdf = d < R ? leafSdf(qTop) : 1e6;

                // Shade cast by the curl: from its edges onto what lies under them, and beyond the
                // cylinder onto the page underneath.
                float soft = 5.0 + R * 0.45;
                float shade = 0.0;
                // Under the turned-back paper's edges, a tight contact shadow on the page it lies
                // on (the paper is all but touching there).
                if (topSdf > 0.0 && topSdf < 1e5) shade = exp(-topSdf / (2.0 + R * 0.08)) * 0.45;
                if (d >= R) {
                    // Beyond the roll, its shadow on the page underneath: dense at the rim, long and soft.
                    float2 rim = p + (R * PI * 0.5 - d) * normal;
                    float there = clamp(1.0 - leafSdf(rim) / soft, 0.0, 1.0);
                    float t = d - R;
                    float fall = 0.6 * exp(-t / (5.0 + R * 0.2)) + 0.4 * exp(-t / (16.0 + R * 1.3));
                    shade = max(shade, fall * there);
                }
                float strength = dark > 0.5 ? 0.85 : 0.5;
                float s = clamp(shade * strength * shadowAmount, 0.0, 1.0);
                color = half4(color.rgb * half(1.0 - s), color.a + half(s) * (1.0 - color.a));

                if (d < R) {
                    float cover = clamp(0.5 - topSdf, 0.0, 1.0);
                    half4 topColor = backAt(qTop);
                    // The paper's own edge reads as a fine line: darker on a light page, lighter on a
                    // dark one.
                    float edge = clamp(1.0 + topSdf / 1.5, 0.0, 1.0);
                    float rimLine = 1.0 - 0.1 * edge * (1.0 - night);
                    // A dark page's turned-back paper is lifted toward the light, most where it faces
                    // up, with a sheen along the top of the roll.
                    float sheen = d >= 0.0 ? exp(-pow((d / R - 0.35) / 0.3, 2.0)) : 0.0;
                    float lift = night * (0.05 + 0.03 * pow(z, 2.0) + 0.06 * sheen + 0.12 * edge);
                    float3 lit = float3(topColor.rgb) * topLight * rimLine + gloss * (dark > 0.5 ? 0.1 : 0.04) + lift;
                    topColor = half4(half3(clamp(lit, 0.0, 1.0)), 1.0);
                    color = mix(color, topColor, half(cover));
                }
                return color;
            }
        """.trimIndent()
    }
}

/**
 * One frame of a curl, handed to the render thread: the shape, and what to draw it from. Bitmaps are
 * immutable (hardware) snapshots of the page; a missing one is drawn as plain paper.
 */
internal class CurlFrame(
    val width: Float,
    val height: Float,
    val shape: CurlShape,
    /** Canonical coordinates are the screen's mirrored (a turn to the right). */
    val mirror: Boolean,
    /** The turning leaf's front. */
    val front: Bitmap?,
    /** Its flat front is the live page under the overlay (left transparent there). */
    val frontLive: Boolean,
    /** In a spread, the page whose back this is: the next spread's facing page. */
    val back: Bitmap?,
    val backIsNextPage: Boolean,
    /** The page that keeps still beside or under the leaf. */
    val still: Bitmap?,
    val stillEverywhere: Boolean,
    /** The live page underneath already shows where the turn goes. */
    val underLive: Boolean,
    /** Its snapshot, once taken: drawn in place of the live page, so the overlay is opaque. */
    val under: Bitmap?,
    val paper: Int,
    val dark: Boolean,
)
