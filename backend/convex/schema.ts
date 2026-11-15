import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

// The server only ever sees opaque data: a group is addressed by the SHA-256 of a token the
// devices derive from their 12-word phrase, record keys are HMACs, and blobs are AES-GCM
// ciphertext. Nothing here identifies a person, a device, or a book.
export default defineSchema({
  groups: defineTable({
    tokenHash: v.string(),
    // Monotonic per-group change counter; every accepted write takes the next value.
    seq: v.number(),
    createdAt: v.number(),
  }).index("by_tokenHash", ["tokenHash"]),

  records: defineTable({
    groupId: v.id("groups"),
    key: v.string(),
    // null marks a deletion so other devices remove the item too.
    blob: v.union(v.string(), v.null()),
    // Hybrid logical clock, encoded so plain string comparison orders it.
    hlc: v.string(),
    seq: v.number(),
  })
    .index("by_groupId_and_key", ["groupId", "key"])
    .index("by_groupId_and_seq", ["groupId", "seq"]),
});
