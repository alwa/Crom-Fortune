package com.sundbybergsit.cromfortune.domain.notifications

interface NotificationsRepository {

    fun list(): Set<NotificationMessage>

    fun add(notificationMessage: NotificationMessage)

    fun remove(notificationMessage: NotificationMessage)

    fun clear()

}
