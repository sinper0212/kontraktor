package org.nustaq.reallive.client;

import org.nustaq.kontraktor.Actors;
import org.nustaq.kontraktor.IPromise;
import org.nustaq.kontraktor.Promise;
import org.nustaq.kontraktor.util.Log;
import org.nustaq.reallive.api.RealLiveTable;
import org.nustaq.reallive.api.RecordStorage;
import org.nustaq.reallive.api.TableDescription;
import org.nustaq.reallive.impl.actors.RealLiveTableActor;
import org.nustaq.reallive.impl.storage.CachedOffHeapStorage;
import org.nustaq.reallive.impl.storage.HeapRecordStorage;
import org.nustaq.reallive.impl.storage.OffHeapRecordStorage;

import java.io.File;
import java.util.function.Supplier;

public class EmbeddedRealLive {

    private static EmbeddedRealLive instance = new EmbeddedRealLive();
    public static EmbeddedRealLive get() {
        return instance;
    }

    /**
     * WARNING: never create more than one table using the same file. This will
     * result in corrupted data for sure. As actor refs (tables) are thread save,
     * just init a singleton containing all your tables once.
     *
     * @param desc
     * @param dataDir
     * @return a thread save actor reference to a newly loaded or created table
     */
    public IPromise<RealLiveTable> createTable(TableDescription desc, String dataDir) {
        RealLiveTableActor table = Actors.AsActor(RealLiveTableActor.class);

        Supplier<RecordStorage> memFactory;
        if (desc.getFilePath() == null) {
            Log.Info(this,"no file specified. all data in memory "+desc.getName());
            switch (desc.getStorageType()) {
                case CACHED:
                    memFactory = () -> new CachedOffHeapStorage(
                        new OffHeapRecordStorage(desc.getKeyLen(), desc.getSizeMB(), desc.getNumEntries()),
                        new HeapRecordStorage());
                    break;
                default:
                    Log.Error(this,"unknown storage type "+desc.getStorageType()+" default to PERSIST");
                case PERSIST:
                    memFactory = () -> new OffHeapRecordStorage(desc.getKeyLen(), desc.getSizeMB(), desc.getNumEntries());
                    break;
                case TEMP:
                    memFactory = () -> new HeapRecordStorage();
                    break;
            }
        } else {
            String bp = dataDir == null ? desc.getFilePath() : dataDir;
            desc.filePath(bp);
            new File(bp).mkdirs();
            String file = bp + "/" + desc.getName() + "_" + desc.getShardNo() + ".bin";
            switch (desc.getStorageType()) {
                case CACHED:
                    Log.Info(this,"memory mapping file "+file);
                    memFactory = () -> new CachedOffHeapStorage(
                        new OffHeapRecordStorage(
                            file,
                            desc.getKeyLen(),
                            desc.getSizeMB(),
                            desc.getNumEntries()
                        ),
                        new HeapRecordStorage()
                    );
                    break;
                default:
                    Log.Error(this,"unknown storage type "+desc.getStorageType()+" default to PERSIST");
                case PERSIST:
                    Log.Info(this,"memory mapping file "+file);
                    memFactory = () ->
                        new OffHeapRecordStorage(
                            file,
                            desc.getKeyLen(),
                            desc.getSizeMB(),
                            desc.getNumEntries()
                        );
                    break;
                case TEMP:
                    memFactory = () -> new HeapRecordStorage();
                    break;
            }
        }
        table.init(memFactory, desc).await(30_000);
        return new Promise(table);
    }

}
