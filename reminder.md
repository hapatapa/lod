# Reminder: LOD Gap-Filling Logic擴展

Currently, we are implementing a "Full Block" water model and dynamic Y-scaling to fill gaps in multi-level water LODs (slopes, waterfalls).

**Future Task:**
Extend this "Gap-Filling" logic to other block types (terrain, cave entries, etc.).

**Logic to replicate:**
- **Lowest LOD Check**: Check the Y-levels of adjacent LOD blocks and scale the current block's Y-face downwards to meet the lowest neighbor.
- **Real Chunk Check**: When adjacent to real chunks, scan downwards at the boundaries until non-air blocks are found to determine the low point.
- **Full Block Models**: Ensure models used for these cases are solid (non-transparent) to avoid internal face rendering issues when scaled.
