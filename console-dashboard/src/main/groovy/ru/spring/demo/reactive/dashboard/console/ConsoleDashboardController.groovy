package ru.spring.demo.reactive.dashboard.console

import groovy.transform.CompileStatic
import lombok.RequiredArgsConstructor
import lombok.extern.slf4j.Slf4j
import org.fusesource.jansi.Ansi
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.ConnectMapping
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import ru.spring.demo.reactive.dashboard.console.model.RateStatus

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import static java.lang.String.format
import static org.fusesource.jansi.Ansi.Color.BLACK
import static org.fusesource.jansi.Ansi.ansi

@Slf4j
@Component
@CompileStatic
@RequiredArgsConstructor
@Controller
class ConsoleDashboardController {
    public static final int PAD = 20

    final ConcurrentMap<String, RateStatus> componentsMap = new ConcurrentHashMap<>()

    ConsoleDashboardController() { }

    @ConnectMapping
    Mono<Void> handleNewService(@Payload String componentName, RSocketRequester connection) {
        connection
            .rsocket()
            .onClose()
            .subscribe(
                null,
                {
                    componentsMap.compute(componentName, {String s, RateStatus status
                        -> status.toBuilder().letterRps(0).status("DOWN").build() })
                },
                {
                    componentsMap.compute(componentName, {String s, RateStatus status
                        -> status.toBuilder().letterRps(0).status("DOWN").build() })
                }
            )

        Mono.empty()
    }

    @MessageMapping("report")
    Mono<Void> reportDashboard(RateStatus rateStatus) {
        Mono.<Void>fromRunnable {
            componentsMap.put(rateStatus.component, rateStatus)
        }
    }

    @Scheduled(fixedDelay = 100L)
    void run() {
        def builder = new StringBuilder()
        def statuses = componentsMap.values()
        int COL0_MAXSIZE = componentsMap.keySet()*.size().max()
        COL0_MAXSIZE = (COL0_MAXSIZE == null ? 0 : COL0_MAXSIZE)
        COL0_MAXSIZE = Math.max(COL0_MAXSIZE, 'service name'.length())
        int TABLE_LINESIZE = PAD * 2 + COL0_MAXSIZE + 8 + 6 + 6 + 1

        ansi().restoreCursorPosition()

        (statuses.size() + 3).times {
            builder.append ansi().cursorUpLine().eraseLine()
        }

        builder.append("┏${('━' * TABLE_LINESIZE)}┓\n")
        builder.append '┃'
        builder.append ansi().fgBright(BLACK)
        builder.append 'service name'.padRight(COL0_MAXSIZE + 4)
        builder.append 'speed'.center(7)
        builder.append 'buffers'.center(12)
        builder.append 'workers'.center(4)
        builder.append 'retries'.center(13)
        builder.append 'drops'.center(4)
        builder.append 'status'.center(12)
        builder.append ' '.padLeft(1)
        builder.append ansi().reset()
        builder.append '┃\n'

        statuses
            .sort(false, { it.order })
            .each { status ->
                builder.append '┃'
                builder.append formatComponent(status, COL0_MAXSIZE)
                builder.append "${formatRps(status)} ${formatBuffer(status)}"
                builder.append "${formatFps(status)}".padLeft(18)
                builder.append "${formatDps(status)}".padLeft(17)
                builder.append "${formatStatus(status)}".center(20)
                builder.append '┃\n'.padLeft(3)
            }

        builder.append("┗${('━' * TABLE_LINESIZE)}┛\n")

        print builder
    }

    private String formatBuffer(RateStatus status) {
        def buffer = status.buffers?.get(0)
        return buffer?.with {
            def result = ansi()

            if (remaining <= maxSize * 0.75) {
                result.fgBrightRed()
            } else {
                result.fgBrightGreen()
            }


            result.format('%5d/%-3d', remaining, maxSize)
            result.format('%6d/%-2d', activeWorker, workersCount)
            return result.reset().toString()
        } ?: format('%5d/%-3d%6d/%-2d', 0, 0, 0, 0)
    }

    private static String formatRps(RateStatus status) {
        ansi().fgBrightCyan().format('%8.2f', status.getLetterRps()).reset().toString()
    }

    private static String formatFps(RateStatus status) {
        ansi().fgBrightCyan().format('%8.2f', status.getLetterFps()).reset().toString()
    }

    private static String formatDps(RateStatus status) {
        ansi().fgBrightCyan().format('%8.2f', status.getLetterDps()).reset().toString()
    }

    private static String formatComponent(RateStatus status, int colSize) {
        Ansi out
        if (status.status == "UP") {
            out = ansi().fgBrightGreen()
        }
        else {
            out = ansi().fgBrightRed()
        }

        out.a(status.getComponent().padRight(colSize + 2, '.')).reset().toString()
    }

    private static String formatStatus(RateStatus status) {
        Ansi out
        if (status.status == "UP") {
            out = ansi().fgBrightGreen()
        }
        else {
            out = ansi().fgBrightRed()
        }

        out.a(status.status).reset().toString()
    }
}
