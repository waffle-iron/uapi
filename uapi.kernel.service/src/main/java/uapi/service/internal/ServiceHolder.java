package uapi.service.internal;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import rx.Observable;
import uapi.KernelException;
import uapi.helper.ArgumentChecker;
import uapi.helper.StringHelper;
import uapi.service.*;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The ServiceHolder hold specific service with its id and dependencies
 */
class ServiceHolder implements IServiceReference {

    private final Object _svc;
    private final String _svcId;
    private final Multimap<String, ServiceHolder> _dependencies;
    private boolean _inited = false;
    private final ISatisfyHook _satisfyHook;

    ServiceHolder(
            final Object service,
            final String serviceId,
            final ISatisfyHook satisfyHook
    ) {
        this(service, serviceId, new String[0], satisfyHook);
    }

    ServiceHolder(
            final Object service,
            final String serviceId,
            final String[] dependencies,
            final ISatisfyHook satisfyHook
    ) {
        ArgumentChecker.notNull(service, "service");
        ArgumentChecker.notEmpty(serviceId, "serviceId");
        ArgumentChecker.notNull(dependencies, "dependencies");
        ArgumentChecker.notNull(satisfyHook, "satisfyHook");
        this._svc = service;
        this._svcId = serviceId;
        this._satisfyHook = satisfyHook;
        this._dependencies = LinkedListMultimap.create();
        Stream.of(dependencies).forEach(dependency -> this._dependencies.put(dependency, null));
    }

    @Override
    public String getId() {
        return this._svcId;
    }

    @Override
    public Object getService() {
        return this._svc;
    }

    void setDependency(ServiceHolder service) {
        ArgumentChecker.notNull(service, "service");
//        if (! service.isSatisfied()) {
//            throw new KernelException("The service {} is not resolved", service._svcId);
//        }
        if (! this._dependencies.containsKey(service.getId())) {
            throw new KernelException("The service {} does not depend on service {}", this._svcId, service._svcId);
        }
        // remove null entry first
        this._dependencies.remove(service.getId(), null);
        this._dependencies.put(service.getId(), service);
    }

    boolean isDependsOn(final String serviceId) {
        ArgumentChecker.notEmpty(serviceId, "serviceId");
        return this._dependencies.containsKey(serviceId);
    }

    boolean isInited() {
        return this._inited;
    }

    boolean isSatisfied() {
        Optional<Map.Entry<String, ServiceHolder>> unresolvedSvc =
                this._dependencies.entries().stream()
                        .filter(entry -> entry.getValue() == null)
                        .filter(entry -> ! ((IInjectable) this._svc).isOptional(entry.getKey()))
                        .findFirst();
        if (unresolvedSvc.isPresent()) {
            return false;
        }
        return this._satisfyHook.isSatisfied(this._svc);
    }

    void initService() {
        if (this._inited) {
            return;
        }
        if (! isSatisfied()) {
            throw new KernelException("Unsatisfied service can't be initialized");
        }
        if (this._dependencies.size() > 0) {
            if (this._svc instanceof IInjectable) {
                Observable.from(this._dependencies.values())
                        .filter(dependency -> dependency != null)
                        .doOnNext(ServiceHolder::initService)
                        .subscribe(dependency -> {
                            Object injectedSvc = dependency.getService();
                            if (injectedSvc instanceof IServiceFactory) {
                                injectedSvc = ((IServiceFactory) injectedSvc).createService(this._svc);
                            }
                            ((IInjectable) this._svc).injectObject(new Injection(dependency.getId(), injectedSvc));
                        });
            } else {
                throw new KernelException("The service {} does not implement IInjectable interface so it can't inject any dependencies");
            }
        }
        if (this._svc instanceof IInitial) {
            ((IInitial) this._svc).init();
        }
        this._inited = true;
    }

    @Override
    public String toString() {
        return StringHelper.makeString("Service[id={}, type={}, dependencies={}]",
                this._svcId, this._svc.getClass().getName(), this._dependencies);
    }
}
