package org.unidal.cat.plugin.transaction.filter;

import com.dianping.cat.Constants;
import com.dianping.cat.consumer.transaction.model.entity.*;
import com.dianping.cat.consumer.transaction.model.transform.BaseVisitor;
import org.unidal.cat.plugin.transaction.TransactionConstants;
import org.unidal.cat.spi.remote.RemoteContext;
import org.unidal.cat.spi.report.ReportFilter;
import org.unidal.lookup.annotation.Inject;
import org.unidal.lookup.annotation.Named;

@Named(type = ReportFilter.class, value = TransactionConstants.NAME + ":" + TransactionAllNameGraphFilter.ID)
public class TransactionAllNameGraphFilter implements ReportFilter<TransactionReport> {
    public static final String ID = "all-name-graph";

    @Inject
    private TransactionReportHelper m_helper;

    @Override
    public String getReportName() {
        return TransactionConstants.NAME;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public TransactionReport screen(RemoteContext ctx, TransactionReport report) {
        String type = ctx.getProperty("type", null);
        String name = ctx.getProperty("name", null);
        NameGraphScreener visitor = new NameGraphScreener(report.getDomain(), type, name);

        report.accept(visitor);
        return visitor.getReport();
    }

    @Override
    public void tailor(RemoteContext ctx, TransactionReport report) {
        String type = ctx.getProperty("type", null);
        String name = ctx.getProperty("name", null);
        String ip = ctx.getProperty("ip", null);
        NameGraphTailor visitor = new NameGraphTailor(ip, type, name);

        report.accept(visitor);
    }

    private class NameGraphScreener extends BaseVisitor {
        private String m_type;

        private String m_name;

        private TransactionHolder m_holder = new TransactionHolder();

        public NameGraphScreener(String domain, String type, String name) {
            m_type = type;
            m_name = name;
            m_holder.setReport(new TransactionReport(domain));
        }

        public TransactionReport getReport() {
            return m_holder.getReport();
        }

        private void mergeName(TransactionName n, TransactionName name) {
            m_helper.mergeName(n, name);
            n.setSuccessMessageUrl(null);
            n.setFailMessageUrl(null);
        }

        private void mergeType(TransactionType t, TransactionType type) {
            m_helper.mergeType(t, type);
            t.setSuccessMessageUrl(null);
            t.setFailMessageUrl(null);
        }

        @Override
        public void visitMachine(Machine machine) {
            Machine machineAll = m_holder.getMachine();
            TransactionType type = machine.findType(m_type);

            if (machineAll != null) {
                m_helper.mergeMachine(machineAll, machine);

                if (type != null) {
                    TransactionType ta = machineAll.findOrCreateType(m_type);
                    m_holder.setType(ta);
                    visitType(type);
                }
            }
        }

        @Override
        public void visitName(TransactionName name) {
            TransactionName na = m_holder.getName();

            if (na != null) {
                mergeName(na, name);
                m_helper.mergeDurations(na.getDurations(), name.getDurations());
                m_helper.mergeRanges(na.getRanges(), name.getRanges());
            }
        }

        @Override
        public void visitTransactionReport(TransactionReport report) {
            TransactionReport r = m_holder.getReport();

            m_helper.mergeReport(r, report);

            Machine machineAll = r.findOrCreateMachine(Constants.ALL);

            m_holder.setMachine(machineAll);

            for (Machine machine : report.getMachines().values()) {
                visitMachine(machine);
            }
        }

        @Override
        public void visitType(TransactionType type) {
            TransactionType ta = m_holder.getType();
            TransactionName name = type.findName(m_name);

            if (name != null && ta != null) {
                TransactionName na = ta.findOrCreateName(m_name);

                mergeType(ta, type);
                m_holder.setName(na);
                visitName(name);
            }
        }
    }

    private class NameGraphTailor extends BaseVisitor {
        private String m_ip;

        private String m_type;

        private String m_name;

        private TransactionHolder m_holder = new TransactionHolder();

        public NameGraphTailor(String ip, String type, String name) {
            m_ip = ip;
            m_type = type;
            m_name = name;
        }

        @Override
        public void visitMachine(Machine machine) {
            TransactionType type = machine.findType(m_type);

            machine.getTypes().clear();

            if (type != null) {
                TransactionType t = m_holder.getMachine().findOrCreateType(type.getId());

                machine.addType(type);
                m_helper.mergeType(t, type);
                m_holder.setType(t);
            }

            super.visitMachine(machine);
        }

        @Override
        public void visitTransactionReport(TransactionReport report) {
            boolean all = m_ip == null || m_ip.equals(Constants.ALL);

            if (all) {
                Machine machine = new Machine(Constants.ALL);

                m_holder.setMachine(machine);

                report.getTypeDomains().clear();
            } else {
                Machine machine = new Machine(m_ip);
                Machine m = report.findMachine(m_ip);

                report.getMachines().clear();
                report.addMachine(m);
                m_holder.setMachine(machine);

                TypeDomain typeDomain = report.findOrCreateTypeDomain(m_type);
                report.getTypeDomains().clear();
                if (typeDomain != null) {
                    report.addTypeDomain(typeDomain);
                }
            }

            super.visitTransactionReport(report);

            report.addMachine(m_holder.getMachine());
        }

        @Override
        public void visitType(TransactionType type) {
            TransactionType t = m_holder.getType();
            TransactionName n = t.findOrCreateName(m_name);
            TransactionName name = type.findName(m_name);

            t.setSuccessMessageUrl(null);
            t.setFailMessageUrl(null);
            t.setSlowestMessageUrl(null);
            type.setSuccessMessageUrl(null);
            type.setFailMessageUrl(null);
            type.setSlowestMessageUrl(null);
            type.getNames().clear();

            if (name != null) {
                m_helper.mergeName(n, name);
                n.setSuccessMessageUrl(null);
                n.setFailMessageUrl(null);
                n.setSlowestMessageUrl(null);
                m_helper.mergeDurations(n.getDurations(), name.getDurations());
                m_helper.mergeRanges(n.getRanges(), name.getRanges());

                type.addName(name);
                name.getRanges().clear();
                name.getDurations().clear();
                name.setSuccessMessageUrl(null);
                name.setFailMessageUrl(null);
                name.setSlowestMessageUrl(null);
            }
        }

        @Override
        public void visitTypeDomain(TypeDomain typeDomain) {
            typeDomain.getBus().clear();

            NameDomain nameDomain = typeDomain.findNameDomain(m_name);
            typeDomain.getNameDomains().clear();

            if (nameDomain != null) {
                typeDomain.addNameDomain(nameDomain);
            }

            super.visitTypeDomain(typeDomain);
        }

        @Override
        public void visitNameDomain(NameDomain nameDomain) {
            Bu bu = nameDomain.findBu(m_ip);

            nameDomain.getBus().clear();
            if (bu != null) {
                nameDomain.addBu(bu);
            }
        }
    }
}
