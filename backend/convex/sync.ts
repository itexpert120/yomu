import { v, ConvexError } from "convex/values";
import { mutation, query, QueryCtx } from "./_generated/server";
import { Doc } from "./_generated/dataModel";

// Upper bounds that keep any one call well inside Convex's per-function limits.
const MAX_PUSH = 200;
const MAX_PULL = 500;
const MAX_DELETE = 1000;
const MAX_BLOB = 512 * 1024;

// Wire-protocol versioning. Bump PROTOCOL on any change an existing app can't handle, and raise
// MIN_CLIENT_PROTOCOL when old apps must stop syncing; they then show "Update Yomu to keep syncing".
const PROTOCOL = 1;
const MIN_CLIENT_PROTOCOL = 1;

const tokenArg = v.string();
const protocolArg = v.number();

function checkClient(protocol: number) {
  if (protocol < MIN_CLIENT_PROTOCOL) throw new ConvexError("client_outdated");
}

const recordArg = v.object({
  key: v.string(),
  blob: v.union(v.string(), v.null()),
  hlc: v.string(),
});

const recordOut = v.object({
  key: v.string(),
  blob: v.union(v.string(), v.null()),
  hlc: v.string(),
  seq: v.number(),
});

async function tokenHash(token: string): Promise<string> {
  // 32 random bytes, base64url-encoded by the app. Anything else is not a Yomu sync token.
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new ConvexError("invalid_token");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function findGroup(ctx: QueryCtx, token: string): Promise<Doc<"groups"> | null> {
  const hash = await tokenHash(token);
  return await ctx.db
    .query("groups")
    .withIndex("by_tokenHash", (q) => q.eq("tokenHash", hash))
    .unique();
}

async function requireGroup(ctx: QueryCtx, token: string): Promise<Doc<"groups">> {
  const group = await findGroup(ctx, token);
  if (group === null) throw new ConvexError("unknown_group");
  return group;
}

/** Lets the app check compatibility before syncing. */
export const info = query({
  args: {},
  returns: v.object({ protocol: v.number(), minClientProtocol: v.number() }),
  handler: async () => ({ protocol: PROTOCOL, minClientProtocol: MIN_CLIENT_PROTOCOL }),
});

/** Creates the sync group for a freshly generated phrase. Idempotent. */
export const create = mutation({
  args: { protocol: protocolArg, token: tokenArg },
  returns: v.boolean(),
  handler: async (ctx, { protocol, token }) => {
    checkClient(protocol);
    if ((await findGroup(ctx, token)) !== null) return true;
    await ctx.db.insert("groups", { tokenHash: await tokenHash(token), seq: 0, createdAt: Date.now() });
    return true;
  },
});

/** Whether a typed-in phrase belongs to an existing group, so joining never creates one by accident. */
export const exists = query({
  args: { protocol: protocolArg, token: tokenArg },
  returns: v.boolean(),
  handler: async (ctx, { protocol, token }) => {
    checkClient(protocol);
    return (await findGroup(ctx, token)) !== null;
  },
});

/**
 * Stores each record unless the server already holds a newer version of it (higher HLC), so
 * last-writer-wins holds without the server ever reading the encrypted contents.
 */
export const push = mutation({
  args: { protocol: protocolArg, token: tokenArg, records: v.array(recordArg) },
  returns: v.object({ head: v.number() }),
  handler: async (ctx, { protocol, token, records }) => {
    checkClient(protocol);
    if (records.length > MAX_PUSH) throw new ConvexError("too_many_records");
    const group = await requireGroup(ctx, token);
    let seq = group.seq;
    for (const record of records) {
      if (record.key.length > 128 || record.hlc.length > 64) throw new ConvexError("invalid_record");
      if (record.blob !== null && record.blob.length > MAX_BLOB) throw new ConvexError("record_too_large");
      const existing = await ctx.db
        .query("records")
        .withIndex("by_groupId_and_key", (q) => q.eq("groupId", group._id).eq("key", record.key))
        .unique();
      if (existing !== null && existing.hlc >= record.hlc) continue;
      seq += 1;
      if (existing === null) {
        await ctx.db.insert("records", { groupId: group._id, ...record, seq });
      } else {
        await ctx.db.patch("records", existing._id, { blob: record.blob, hlc: record.hlc, seq });
      }
    }
    if (seq !== group.seq) await ctx.db.patch("groups", group._id, { seq });
    return { head: seq };
  },
});

/**
 * Changes after [after], oldest first. Clients subscribe to this with their cursor, so new writes
 * from other devices arrive live.
 */
export const pull = query({
  args: { protocol: protocolArg, token: tokenArg, after: v.number() },
  returns: v.object({ records: v.array(recordOut), head: v.number() }),
  handler: async (ctx, { protocol, token, after }) => {
    checkClient(protocol);
    const group = await requireGroup(ctx, token);
    const page = await ctx.db
      .query("records")
      .withIndex("by_groupId_and_seq", (q) => q.eq("groupId", group._id).gt("seq", after))
      .take(MAX_PULL);
    return {
      records: page.map(({ key, blob, hlc, seq }) => ({ key, blob, hlc, seq })),
      head: group.seq,
    };
  },
});

/**
 * Deletes everything stored for the group, a batch at a time; call until it returns done = true.
 * The group row goes last so the remaining batches can still find their records.
 */
export const destroy = mutation({
  args: { protocol: protocolArg, token: tokenArg },
  returns: v.object({ done: v.boolean() }),
  handler: async (ctx, { protocol, token }) => {
    checkClient(protocol);
    const group = await findGroup(ctx, token);
    if (group === null) return { done: true };
    const batch = await ctx.db
      .query("records")
      .withIndex("by_groupId_and_seq", (q) => q.eq("groupId", group._id))
      .take(MAX_DELETE);
    for (const record of batch) await ctx.db.delete("records", record._id);
    if (batch.length < MAX_DELETE) {
      await ctx.db.delete("groups", group._id);
      return { done: true };
    }
    return { done: false };
  },
});
