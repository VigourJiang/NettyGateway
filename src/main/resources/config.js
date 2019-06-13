var CONFIG = {
    server: {
        authority_proxy: 'http://192.168.140.100:8080',
        monitor_proxy: 'http://127.0.0.1:9011',
        topology_proxy: 'http://127.0.0.1:9010',
        listen_host: 'localhost',
        listen_port: 9092,
    },
    ui: {
        title: '徐州ACC监控系统',
    },
    menu: [
        {
            function: 'system_monitor',
            path: '',
            icon: '',
            title: '系统监控',
            viewId: '',
            subMenus: [
                {
                    function: '$#a',
                    path: '/monitor',
                    icon: 'fund',
                    title: '路网监控',
                    viewId: 'NodeMonitor',
                },
                {
                    function: '$#b',
                    path: '/passenger',
                    icon: 'rise',
                    title: '客流监控',
                    viewId: 'PassengerFlowManager',
                },
                {
                    function: '$#c',
                    path: '/section',
                    icon: 'rise',
                    title: '断面客流',
                    viewId: 'SectionFlowManager',
                },
                {
                    function: '$#d',
                    path: '/node',
                    icon: 'edit',
                    title: '节点管理',
                    viewId: 'NodeManager',
                },
                {
                    function: '$#e',
                    path: '/query',
                    icon: 'search',
                    title: '各类查询',
                    viewId: 'QueryManager',
                },
            ],
        },
    ],
};

function getUiConfig() {
    return JSON.stringify(CONFIG.ui);
}

function getMenuConfig() {
    return JSON.stringify(CONFIG.menu);
}