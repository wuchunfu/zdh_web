
(function(document, window, $) {

    // Example Bootstrap Table Events
    // ------------------------------
    (function() {
        var height=400;
        if($(document.body).height()*0.8>height){
            height=$(document.body).height()*0.8
        }
        $('#exampleTableEvents').attr("data-height",height);
        $('#add').click(function () {
            parent.layer.open({
                type: 2,
                title: '外部JAR任务配置',
                shadeClose: false,
                resize: true,
                fixed: false,
                maxmin: true,
                shade: 0.1,
                area : ['45%', '60%'],
                //area: ['450px', '500px'],
                content: server_context+"/etl_task_jar_add_index?id=-1", //iframe的url
                end:function () {
                    $('#exampleTableEvents').bootstrapTable('refresh', {
                        url : server_context+'/etl_task_jar_list'
                    });
                }
            });
        });

        $('#remove').click(function () {

            var rows = $("#exampleTableEvents").bootstrapTable('getSelections');// 获得要删除的数据
            if (rows.length == 0) {// rows 主要是为了判断是否选中，下面的else内容才是主要
                layer.msg("请先选择要删除的记录!");
                return;
            } else {
                layer.confirm('是否删除外部jar任务', {
                    btn: ['确定','取消'] //按钮
                }, function(index){
                    var ids = new Array();// 声明一个数组
                    $(rows).each(function() {// 通过获得别选中的来进行遍历
                        ids.push(this.id);// cid为获得到的整条数据中的一列
                    });
                    console.log(ids);
                    deleteMs(ids);
                    layer.close(layer.index);
                }, function(){

                });

            }

        })

        function deleteMs(ids) {
            $.ajax({
                url : server_context+"/etl_task_jar_delete",
                data : "ids=" + ids,
                type : "post",
                dataType : "json",
                success : function(data) {
                    console.info("success");
                    $('#exampleTableEvents').bootstrapTable('refresh', {
                        url : server_context+'/etl_task_jar_list'
                    });
                },
                error: function (data) {
                    console.info("error: " + data.responseText);
                }

            });
        }

        window.operateEvents = {
            'click #edit': function (e, value, row, index) {
                $("#id").val(row.id);
                top.layer.open({
                    type: 2,
                    title: '外部JAR配置',
                    shadeClose: false,
                    resize: true,
                    fixed: false,
                    maxmin: true,
                    shade: 0.1,
                    area : ['45%', '60%'],
                    //area: ['450px', '500px'],
                    content: server_context+"/etl_task_jar_add_index?id="+row.id, //iframe的url
                    end:function () {
                        $('#exampleTableEvents').bootstrapTable('refresh', {
                            url : server_context+'/etl_task_jar_list'
                        });
                    }
                });

            },
            'click #del': function (e, value, row, index) {
                layer.confirm('是否删除任务', {
                    btn: ['确定','取消'] //按钮
                }, function(index){
                    var ids = new Array();// 声明一个数组
                    ids.push(row.id);
                    deleteMs(ids);
                    layer.close(layer.index)
                }, function(){

                });

            }
        };

        function operateFormatter(value, row, index) {
            return [
                ' <div class="btn-group hidden-xs" id="exampleTableEventsToolbar" role="group">' +
                ' <button id="edit" name="edit" type="button" class="btn btn-outline btn-sm" title="更新"><i class="glyphicon glyphicon-edit" aria-hidden="true"></i>\n' +
                '                                    </button>',
                ' <button id="del" name="del" type="button" class="btn btn-outline btn-sm" title="删除">\n' +
                '                                        <i class="glyphicon glyphicon-trash" aria-hidden="true"></i>\n' +
                '                                    </button>'
                +
                '</div>'

            ].join('');

        }

        //表格超出宽度鼠标悬停显示td内容
        function paramsMatter(value, row, index) {
            var span = document.createElement("span");
            span.setAttribute("title", value);
            span.innerHTML = value;
            return span.outerHTML;
        }
        //td宽度以及内容超过宽度隐藏
        function formatTableUnit(value, row, index) {
            return {
                css: {
                    "white-space": "nowrap",
                    "text-overflow": "ellipsis",
                    "overflow": "hidden",
                    "max-width": "40px"
                }
            }
        }

        function getMyDate(str){
            var oDate = new Date(str),
                oYear = oDate.getFullYear(),
                oMonth = oDate.getMonth()+1,
                oDay = oDate.getDate(),
                oHour = oDate.getHours(),
                oMin = oDate.getMinutes(),
                oSen = oDate.getSeconds(),
                oTime = oYear +'-'+ getzf(oMonth) +'-'+ getzf(oDay) +" "+getzf(oHour)+":"+getzf(oMin)+":"+getzf(oSen);//最后拼接时间
            return oTime;
        };
        //补0操作
        function getzf(num){
            if(parseInt(num) < 10){
                num = '0'+num;
            }
            return num;
        }


        $('#exampleTableEvents').bootstrapTable({
            url: server_context+"/etl_task_jar_list",
            search: true,
            pagination: true,
            pageSize : 10,
            pageList: [10, 20, 50, 100],
            showRefresh: true,
            showToggle: true,
            showColumns: true,
            fixedColumns: true,
            fixedNumber: 3,
            iconSize: 'outline',
            toolbar: '#exampleTableEventsToolbar',
            icons: {
                refresh: 'glyphicon-repeat',
                toggle: 'glyphicon-list-alt',
                columns: 'glyphicon-list'
            },
            columns: [{
                checkbox: true,
                field:'state',
                sortable:true
            }, {
                field: 'id',
                title: 'ID',
                sortable:false
            }, {
                field: 'etl_context',
                title: 'etl 中 文 描 述 及 说 明',
                sortable:false
            },{
                field: 'master',
                title: '集群信息',
                sortable:true
            },{
                field: 'cpu',
                title: 'CPU资源',
                sortable:true
            },{
                field: 'memory',
                title: '内存资源',
                sortable:false
            },{
                field: 'main_class',
                title: '启动类',
                sortable:false,
                cellStyle: formatTableUnit,
                formatter: paramsMatter
            },{
                field: 'create_time',
                title: '任 务 创 建 时 间',
                sortable:true,
                formatter: function (value, row, index) {
                    return getMyDate(value);
                }
            },{
                field: 'operate',
                title: '按钮事件',
                events: operateEvents,//给按钮注册事件
                width:100,
                formatter: operateFormatter //表格中增加按钮
            }]
        });

        var $result = $('#examplebtTableEventsResult');
    })();
})(document, window, jQuery);
