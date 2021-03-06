/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *       Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.runtime.test.runner;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.nuxeo.runtime.management.jvm.ThreadDeadlocksDetector;
import org.nuxeo.runtime.test.runner.HotDeployer.ActionHandler;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * The transactional feature is responsible of transaction management.
 * <p/>
 * It brings some API to wait for transaction
 *
 * @since 10.2
 */
@Deploy("org.nuxeo.runtime.jtajca")
@Deploy("org.nuxeo.runtime.datasource")
@Features(RuntimeFeature.class)
public class TransactionalFeature extends SimpleFeature {

    private static final Log log = LogFactory.getLog(TransactionalFeature.class);

    protected boolean autoStartTransaction;

    protected boolean txStarted;

    protected final List<Waiter> waiters = new LinkedList<>();

    @FunctionalInterface
    public interface Waiter {

        boolean await(long deadline) throws InterruptedException;

    }

    public void addWaiter(Waiter waiter) {
        waiters.add(waiter);
    }

    public void nextTransaction() {
        nextTransaction(10, TimeUnit.MINUTES);
    }

    public void nextTransaction(long duration, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(duration, unit);
        boolean tx = TransactionHelper.isTransactionActive();
        boolean rb = TransactionHelper.isTransactionMarkedRollback();
        if (tx || rb) {
            // there may be tx synchronizer pending, so we
            // have to commit the transaction
            TransactionHelper.commitOrRollbackTransaction();
        }
        try {
            for (Waiter provider : waiters) {
                try {
                    Assert.assertTrue(await(provider, deadline));
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("interrupted while awaiting for asynch completion", cause);
                }
            }
        } finally {
            if (tx || rb) {
                // restore previous tx status
                TransactionHelper.startTransaction();
                if (rb) {
                    TransactionHelper.setTransactionRollbackOnly();
                }
            }
        }
    }

    boolean await(Waiter waiter, long deadline) throws InterruptedException {
        if (waiter.await(deadline)) {
            return true;
        }
        try {
            File file = new ThreadDeadlocksDetector().dump(new long[0]);
            log.warn("timed out in " + waiter.getClass() + ", thread dump available in " + file);
        } catch (IOException cause) {
            log.warn("timed out in " + waiter.getClass() + ", cannot take thread dump", cause);
        }
        return false;
    }

    @Override
    public void initialize(FeaturesRunner runner) {
        autoStartTransaction = runner.getConfig(TransactionalConfig.class).autoStart();
        runner.getFeature(RuntimeFeature.class).registerHandler(new TransactionalDeployer());
    }

    @Override
    public void beforeSetup(FeaturesRunner runner) {
        startTransactionBefore();
    }

    @Override
    public void afterTeardown(FeaturesRunner runner) {
        commitOrRollbackTransactionAfter();
    }

    protected void startTransactionBefore() {
        if (autoStartTransaction) {
            txStarted = TransactionHelper.startTransaction();
        }
    }

    protected void commitOrRollbackTransactionAfter() {
        if (txStarted) {
            TransactionHelper.commitOrRollbackTransaction();
        } else {
            if (TransactionHelper.isTransactionActive()) {
                try {
                    TransactionHelper.setTransactionRollbackOnly();
                    TransactionHelper.commitOrRollbackTransaction();
                } finally {
                    log.warn("Committing a transaction for your, please do it yourself");
                }
            }
        }
    }

    /**
     * Handler used to commit transaction before next action and start a new one after next action if
     * {@link TransactionalConfig#autoStart()} is true. This is because framework is about to be reloaded, then a new
     * transaction manager will be installed.
     *
     * @since 10.2
     */
    public class TransactionalDeployer extends ActionHandler {

        @Override
        public void exec(String action, String... args) throws Exception {
            commitOrRollbackTransactionAfter();
            next.exec(action, args);
            startTransactionBefore();
        }

    }
}
