package com.raulh.randomreminders.di

import javax.inject.Qualifier

/** Qualifier do escopo de corrotinas da aplicação (vive além dos receivers). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
