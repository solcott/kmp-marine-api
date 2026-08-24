package io.github.solcott.marineapi.example.android.di

import android.content.ContentResolver
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.solcott.marineapi.example.android.data.BluetoothGpsRepository
import io.github.solcott.marineapi.example.android.data.ContentResolverNmeaLogRepository
import io.github.solcott.marineapi.example.android.data.GpsRepository
import io.github.solcott.marineapi.example.android.data.NmeaLogRepository
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatcher the data layer reads on.
 *
 * A qualifier rather than a bare `CoroutineDispatcher` binding, because "the dispatcher" is not a
 * thing an app has one of. It exists mainly so a test can hand a repository a deterministic one:
 * `Dispatchers.IO` is named in exactly one place in this app, which is [DispatcherModule] below.
 *
 * This is the consumer's half of a rule the library states about itself -- `nmeaSentences()` reads
 * blocking sources on the collecting coroutine and never picks a dispatcher, because there is no
 * one dispatcher to pick across JVM, Native and JS. Choosing is the application's job, and this is
 * the application making that choice once.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) internal annotation class IoDispatcher

/** The two things the data layer needs from the platform and cannot construct itself. */
@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {

  @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

  /**
   * The application's [ContentResolver], which is what turns a picked `Uri` into an `InputStream`.
   *
   * Taken from the application context rather than the activity's on purpose: the resolver outlives
   * any one activity, and a repository that held an activity context would leak it across a
   * rotation -- the very thing the ViewModels above it exist to survive.
   */
  @Provides
  fun contentResolver(@ApplicationContext context: Context): ContentResolver =
    context.contentResolver
}

/**
 * Binds each repository interface to its implementation.
 *
 * The interfaces are not ceremony here: they are what lets the ViewModel tests run as plain JVM
 * unit tests, with fakes in place of a `ContentResolver` and a Bluetooth radio.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {

  @Binds fun logs(impl: ContentResolverNmeaLogRepository): NmeaLogRepository

  @Binds fun gps(impl: BluetoothGpsRepository): GpsRepository
}
