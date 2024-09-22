package net.world.map.loader.parsers;

import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.world.map.loader.config.LoadingBlockConfig;
import net.world.map.structure.collecions.BlockType;
import net.world.map.structure.collecions.PlantBlockCollection;
import net.world.map.structure.config.ChunkConfig;
import net.world.map.structure.config.SectionConfig;
import net.world.map.structure.config.WorldConfig;
import net.world.map.structure.model.Block;
import net.world.map.structure.model.BlockWithMetadata;
import net.world.map.structure.model.Chunk;
import net.world.map.structure.model.metadata.BlockMeta;
import net.world.map.structure.model.metadata.PlantMeta;
import net.world.map.structure.model.metadata.UnderwaterMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ChunkParser {
    public static Optional<Chunk> parse(CompoundTag chunkTag) {
        int version = chunkTag.getInt("DataVersion");

        // https://minecraft.wiki/w/Data_version#List_of_data_versions
        if (version < 2844) // older than 1.18 (21w43a)
            throw new RuntimeException("Unhandled chunk version: " + version);

        if (!chunkTag.getString("Status").endsWith("full")) {
            return Optional.empty();
        }

        ListTag<CompoundTag> sectionsTags = chunkTag.getListTag("sections").asCompoundTagList();
        HashMap<Integer, SectionParser> sections = new HashMap<>();

        for (CompoundTag sectionTag : sectionsTags) {
            if (!sectionTag.getCompoundTag("block_states").containsKey("data"))
                continue;

            SectionParser sectionParser = new SectionParser(sectionTag);
            sections.put(sectionParser.getSectionY(), sectionParser);
        }

        short maxHeight = WorldConfig.MAX_HEIGHT;
        short minHeight = WorldConfig.MIN_HEIGHT;
        int chunkX = chunkTag.getInt("xPos");
        int chunkY = chunkTag.getInt("zPos");

        Chunk chunk = new Chunk(chunkX, chunkY);

        int startBlockX = chunkX * ChunkConfig.CHUNK_SIZE;
        int startBlockY = chunkY * ChunkConfig.CHUNK_SIZE;

        for (int x = 0; x < ChunkConfig.CHUNK_SIZE; x++) {
            for (int y = 0; y < ChunkConfig.CHUNK_SIZE; y++) {
                Map<Class<? extends BlockMeta>, BlockMeta> metadata = new HashMap<>();

                for (short height = maxHeight; height > minHeight; height--) {
                    int sectionHeightPos = Math.floorDiv(height, SectionConfig.SECTION_HEIGHT);

                    if (!sections.containsKey(sectionHeightPos))
                        continue;

                    SectionParser sectionParser = sections.get(sectionHeightPos);
                    BlockType blockType = sectionParser.getBlockType(x, height, y);

                    if (LoadingBlockConfig.IGNORED_BLOCKS.contains(blockType))
                        continue;

                    if (blockType.equals(BlockType.WATER) || PlantBlockCollection.isWaterPlant(blockType)) {
                        UnderwaterMeta meta = (UnderwaterMeta) metadata.get(UnderwaterMeta.class);

                        if (meta == null)
                            metadata.put(UnderwaterMeta.class, new UnderwaterMeta());
                        else
                            meta.incrementDepth();

                        continue;
                    }

                    if (PlantBlockCollection.isPlant(blockType)) {
                        PlantMeta meta = (PlantMeta) metadata.get(PlantMeta.class);

                        if (meta == null)
                            metadata.put(PlantMeta.class, new PlantMeta(blockType));
                        else
                            meta.increasePlantHeight();

                        continue;
                    }

                    int blockGlobalX = startBlockX + x;
                    int blockGlobalY = startBlockY + y;

                    if (metadata.isEmpty()) {
                        chunk.addBlockByLocal(x, y, new Block(blockGlobalX, blockGlobalY, height, blockType));
                    } else {
                        chunk.addBlockByLocal(x, y,
                                new BlockWithMetadata(blockGlobalX, blockGlobalY, height, blockType, metadata));
                    }

                    break;
                }
            }
        }

        return Optional.of(chunk);
    }
}
